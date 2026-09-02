package com.example.securevault.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.example.securevault.logging.CryptoLogger
import com.example.securevault.model.SecureFileItem
import com.example.securevault.util.SafeFileMoveHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

sealed interface DecryptionResult {
  data class Success(val bytes: ByteArray) : DecryptionResult
  data class TooLargeToPreview(val fileSizeBytes: Long) : DecryptionResult
}

sealed interface BlobHandle {
  fun openInputStream(): InputStream?
  fun length(): Long
  fun delete(): Boolean
  val exists: Boolean
}

class FileBlobHandle(val file: File) : BlobHandle {
  override fun openInputStream(): InputStream? = if (file.exists()) FileInputStream(file) else null
  override fun length(): Long = file.length()
  override fun delete(): Boolean = file.delete()
  override val exists: Boolean get() = file.exists()
}

class DocumentBlobHandle(val context: Context, val docFile: DocumentFile) : BlobHandle {
  override fun openInputStream(): InputStream? = try {
    context.contentResolver.openInputStream(docFile.uri)
  } catch (e: Exception) {
    null
  }
  override fun length(): Long = docFile.length()
  override fun delete(): Boolean = docFile.delete()
  override val exists: Boolean get() = docFile.exists()
}

class SecureVaultRepository(
  private val dao: SecureVaultDao,
  private val context: Context,
  val storagePreferences: SecureVaultStoragePreferences = SecureVaultStoragePreferences(context)
) {
  private val TAG = "SecureVaultRepository"

  companion object {
    const val MAX_PREVIEW_SIZE_BYTES = 80L * 1024 * 1024 // 80 MB safety threshold for volatile preview
    const val DEK_TRANSFORMATION = "AES/GCM/NoPadding"
    const val GCM_TAG_LENGTH = 128
    const val CHUNK_SIZE = 1024 * 1024 // 1 MB chunking eliminates Java heap buffering OOM
    val CHUNK_MAGIC = byteArrayOf('S'.code.toByte(), 'V'.code.toByte(), 'C'.code.toByte(), '1'.code.toByte())
    val BACKUP_MAGIC = byteArrayOf('S'.code.toByte(), 'V'.code.toByte(), 'B'.code.toByte(), '1'.code.toByte())

    fun deriveChunkIv(baseIv: ByteArray, chunkIndex: Long): ByteArray {
      val iv = baseIv.copyOf()
      for (i in 0 until 8) {
        val shift = (7 - i) * 8
        val byteVal = ((chunkIndex ushr shift) and 0xFF).toByte()
        iv[4 + i] = (iv[4 + i].toInt() xor byteVal.toInt()).toByte()
      }
      return iv
    }
  }

  fun unwrapDek(item: SecureFileItem, cipher: Cipher): ByteArray? {
    if (item.wrappedDek.isNotEmpty()) {
      CryptoLogger.hardware("KEYSTORE_UNWRAP", "Unwrapping DEK with Android Keystore Hardware Key (AES-256)...")
      val wrappedDekBytes = Base64.decode(item.wrappedDek, Base64.NO_WRAP)
      return cipher.doFinal(wrappedDekBytes)
    }
    return null
  }

  fun getCustomFolderDocument(): DocumentFile? {
    val uriString = storagePreferences.getCustomFolderUriString() ?: return null
    return try {
      val treeUri = Uri.parse(uriString)
      val doc = DocumentFile.fromTreeUri(context, treeUri)
      if (doc != null && doc.exists() && doc.canWrite()) doc else null
    } catch (e: Exception) {
      Log.w(TAG, "Failed to resolve custom folder DocumentFile: ${e.message}")
      null
    }
  }

  fun getVaultDir(location: VaultStorageLocation = storagePreferences.getStorageLocation()): File {
    return when (location) {
      VaultStorageLocation.INTERNAL_SANDBOX -> {
        File(context.filesDir, "securevault").apply {
          if (!exists()) mkdirs()
        }
      }
      VaultStorageLocation.EXTERNAL_APP -> {
        val externalBase = context.getExternalFilesDir(null) ?: context.filesDir
        File(externalBase, "securevault").apply {
          if (!exists()) mkdirs()
          try {
            File(this, ".nomedia").createNewFile()
          } catch (_: Exception) {}
        }
      }
      VaultStorageLocation.PERSISTENT_CUSTOM_FOLDER -> {
        // When a custom SAF tree folder is active, blob IO operates via DocumentFile.
        // Fall back to the internal sandbox directory if a File-based fallback is required.
        File(context.filesDir, "securevault").apply {
          if (!exists()) mkdirs()
        }
      }
    }
  }

  fun findBlobHandle(blobPath: String): BlobHandle? {
    val loc = storagePreferences.getStorageLocation()

    // 1. If currently in PERSISTENT_CUSTOM_FOLDER, check custom SAF tree first
    if (loc == VaultStorageLocation.PERSISTENT_CUSTOM_FOLDER) {
      val customTree = getCustomFolderDocument()
      if (customTree != null) {
        val child = customTree.findFile(blobPath)
        if (child != null && child.exists()) {
          return DocumentBlobHandle(context, child)
        }
      }
    }

    // 2. Check active location's File path
    val activeFile = File(getVaultDir(loc), blobPath)
    if (activeFile.exists()) return FileBlobHandle(activeFile)

    // 3. Fallback search: check custom folder if it wasn't the active location
    if (loc != VaultStorageLocation.PERSISTENT_CUSTOM_FOLDER) {
      val customTree = getCustomFolderDocument()
      if (customTree != null) {
        val child = customTree.findFile(blobPath)
        if (child != null && child.exists()) {
          return DocumentBlobHandle(context, child)
        }
      }
    }

    // 4. Fallback search across standard File locations
    for (candidateLoc in listOf(VaultStorageLocation.INTERNAL_SANDBOX, VaultStorageLocation.EXTERNAL_APP)) {
      val file = File(getVaultDir(candidateLoc), blobPath)
      if (file.exists()) return FileBlobHandle(file)
    }

    return null
  }

  fun findBlobFile(blobPath: String): File? {
    val handle = findBlobHandle(blobPath)
    if (handle is FileBlobHandle) return handle.file
    return null
  }

  val allFiles: Flow<List<SecureFileItem>> = dao.getAllFiles()

  suspend fun importFile(
    uri: Uri,
    cipher: Cipher,
    deleteOriginalAfterImport: Boolean = true,
    onProgress: (bytesWritten: Long, totalBytes: Long) -> Unit = { _, _ -> }
  ): Result<SecureFileItem> = withContext(Dispatchers.IO) {
    var createdDocFile: DocumentFile? = null
    var createdBlobFile: File? = null
    try {
      CryptoLogger.info("IMPORT", "Initiating secure envelope import for URI: $uri")
      // 1. Resolve original filename and MIME type from URI metadata
      var fileName = "secure_document"
      var fileSize: Long = -1

      context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
          val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
          val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
          if (nameIndex != -1) {
            val name = cursor.getString(nameIndex)
            if (!name.isNullOrBlank()) fileName = name
          }
          if (sizeIndex != -1) {
            fileSize = cursor.getLong(sizeIndex)
          }
        }
      }

      val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
      CryptoLogger.info("METADATA", "File: '$fileName' • MIME: $mimeType • Declared Size: ${if (fileSize >= 0) "$fileSize bytes" else "Streamed"}")

      // 2. Generate a random 256-bit software AES Data Encryption Key (DEK)
      CryptoLogger.hardware("DEK_GEN", "Generating ephemeral 256-bit AES-GCM data encryption key (DEK)...")
      val keyGen = KeyGenerator.getInstance("AES").apply { init(256) }
      val dek = keyGen.generateKey()
      val rawDekBytes = dek.encoded

      // 3. Wrap (encrypt) the DEK using the hardware Keystore-backed cipher
      CryptoLogger.hardware("KEYSTORE_WRAP", "Encrypting DEK with Android Keystore Hardware Master Key (AES-256)...")
      val wrappedDekBytes = cipher.doFinal(rawDekBytes)
      val wrappedDekB64 = Base64.encodeToString(wrappedDekBytes, Base64.NO_WRAP)
      val dekWrapIv = cipher.iv
      val dekIvB64 = if (dekWrapIv != null) Base64.encodeToString(dekWrapIv, Base64.NO_WRAP) else ""

      // 4. Software AES-GCM base IV generation for chunked streaming
      val dekCipherForIv = Cipher.getInstance(DEK_TRANSFORMATION)
      dekCipherForIv.init(Cipher.ENCRYPT_MODE, dek)
      val contentIv = dekCipherForIv.iv
      val contentIvB64 = Base64.encodeToString(contentIv, Base64.NO_WRAP)

      // Immediately zero out the raw DEK byte array from memory
      rawDekBytes.fill(0)
      CryptoLogger.hardware("ZEROIZE", "Plaintext DEK bytes purged from volatile memory.")

      // 5. Open target stream based on storage location
      val blobName = UUID.randomUUID().toString()
      val activeLoc = storagePreferences.getStorageLocation()

      val rawOutStream: OutputStream = if (activeLoc == VaultStorageLocation.PERSISTENT_CUSTOM_FOLDER) {
        val customTree = getCustomFolderDocument()
        if (customTree != null) {
          val doc = customTree.createFile("application/octet-stream", blobName)
            ?: throw IllegalStateException("Failed to create blob document in custom folder")
          createdDocFile = doc
          context.contentResolver.openOutputStream(doc.uri)
            ?: throw IllegalStateException("Failed to open output stream for custom folder document")
        } else {
          CryptoLogger.warn("STORAGE_FALLBACK", "No custom folder selected yet. Falling back to internal sandbox.")
          val targetFile = File(getVaultDir(VaultStorageLocation.INTERNAL_SANDBOX), blobName)
          createdBlobFile = targetFile
          FileOutputStream(targetFile)
        }
      } else {
        val targetFile = File(getVaultDir(activeLoc), blobName)
        createdBlobFile = targetFile
        FileOutputStream(targetFile)
      }

      var totalBytesWritten = 0L
      var chunkIndex = 0L
      val plainBuffer = ByteArray(CHUNK_SIZE)

      CryptoLogger.info("STREAM_CIPHER", "Streaming AES-256-GCM chunks (1MB blocks) to target cipher blob: $blobName")

      rawOutStream.use { rawOut ->
        BufferedOutputStream(rawOut, 64 * 1024).use { out ->
          out.write(CHUNK_MAGIC)

          context.contentResolver.openInputStream(uri)?.let { BufferedInputStream(it, 64 * 1024) }?.use { input ->
            while (true) {
              var bytesReadThisChunk = 0
              while (bytesReadThisChunk < CHUNK_SIZE) {
                val r = input.read(plainBuffer, bytesReadThisChunk, CHUNK_SIZE - bytesReadThisChunk)
                if (r == -1) break
                bytesReadThisChunk += r
              }

              if (bytesReadThisChunk == 0) break // EOF

              val chunkIv = deriveChunkIv(contentIv, chunkIndex)
              val chunkCipher = Cipher.getInstance(DEK_TRANSFORMATION)
              chunkCipher.init(Cipher.ENCRYPT_MODE, dek, GCMParameterSpec(GCM_TAG_LENGTH, chunkIv))
              val cipherBytes = chunkCipher.doFinal(plainBuffer, 0, bytesReadThisChunk)

              val len = cipherBytes.size
              out.write((len ushr 24) and 0xFF)
              out.write((len ushr 16) and 0xFF)
              out.write((len ushr 8) and 0xFF)
              out.write(len and 0xFF)
              out.write(cipherBytes)

              totalBytesWritten += bytesReadThisChunk
              chunkIndex++
              if (chunkIndex % 5L == 0L || totalBytesWritten == fileSize) {
                CryptoLogger.info("CHUNK_PROGRESS", "Chunk #$chunkIndex encrypted ($totalBytesWritten bytes total)")
              }
              onProgress(totalBytesWritten, fileSize)
            }
            out.flush()
          } ?: return@withContext Result.failure(IllegalStateException("Could not read file from URI"))
        }
      }

      val actualSize = if (fileSize > 0) fileSize else totalBytesWritten

      // 6. Store metadata with wrapped DEK and respective IVs in the encrypted database
      val item = SecureFileItem(
        originalFileName = fileName,
        mimeType = mimeType,
        fileSizeBytes = actualSize,
        encryptedBlobPath = blobName,
        dateAdded = System.currentTimeMillis(),
        iv = contentIvB64,
        wrappedDek = wrappedDekB64,
        dekIv = dekIvB64
      )

      val generatedId = dao.insertFile(item)
      val savedItem = item.copy(id = generatedId)
      CryptoLogger.success("STORAGE_FLUSH", "Encrypted blob committed: $blobName (${actualSize} bytes) into ${activeLoc.title}")

      // Record updated metadata shadow snapshot for update resilience
      SecureVaultMetadataSafety.recordShadowSnapshot(context, dao.getAllFilesSync())

      // 7. Delete original unencrypted source file if requested ("Move to Vault" behavior)
      if (deleteOriginalAfterImport) {
        CryptoLogger.info("FILE_MOVE", "Deleting unencrypted original source file from storage...")
        val deleted = SafeFileMoveHelper.deleteSourceFile(context, uri)
        if (deleted) {
          CryptoLogger.success("FILE_MOVE", "Original unencrypted file removed from device storage (Move to Vault complete).")
        } else {
          CryptoLogger.warn("FILE_MOVE", "Source file delete permission was not granted by Android SAF provider (Copy kept).")
        }
      }

      Log.i(TAG, "Successfully imported and envelope-encrypted file $fileName ($actualSize bytes) into ${activeLoc.title}")
      Result.success(savedItem)
    } catch (e: Throwable) {
      CryptoLogger.error("IMPORT_FAIL", "Import error: ${e.message}")
      createdDocFile?.delete()
      createdBlobFile?.let { if (it.exists()) it.delete() }
      Log.e(TAG, "Error importing file to SecureVault: ${e.message}", e)
      Result.failure(e)
    }
  }

  suspend fun exportFile(
    item: SecureFileItem,
    destinationUri: Uri,
    cipher: Cipher,
    onProgress: (bytesExported: Long, totalBytes: Long) -> Unit = { _, _ -> }
  ): Result<Unit> = withContext(Dispatchers.IO) {
    try {
      CryptoLogger.info("EXPORT_START", "Initiating stream-decryption export for '${item.originalFileName}'")
      val blobHandle = findBlobHandle(item.encryptedBlobPath)
      if (blobHandle == null || !blobHandle.exists) {
        CryptoLogger.error("EXPORT_FAIL", "Encrypted blob not found in storage")
        return@withContext Result.failure(IllegalStateException("Encrypted blob file does not exist on disk"))
      }

      var unwrappedDekBytesToWipe: ByteArray? = null

      try {
        val dekKey: SecretKeySpec?
        val contentIvBytes: ByteArray?
        val legacyStreamCipher: Cipher?

        if (item.wrappedDek.isNotEmpty()) {
          CryptoLogger.hardware("KEYSTORE_UNWRAP", "Unwrapping DEK with Android Keystore Hardware Key (AES-256)...")
          val wrappedDekBytes = Base64.decode(item.wrappedDek, Base64.NO_WRAP)
          val unwrappedDekBytes = cipher.doFinal(wrappedDekBytes)
          unwrappedDekBytesToWipe = unwrappedDekBytes
          dekKey = SecretKeySpec(unwrappedDekBytes, "AES")
          contentIvBytes = Base64.decode(item.iv, Base64.NO_WRAP)
          legacyStreamCipher = null
          CryptoLogger.hardware("DEK_READY", "DEK unwrapped. Authenticating AES-GCM tags during streaming...")
        } else {
          dekKey = null
          contentIvBytes = null
          legacyStreamCipher = cipher
        }

        var totalBytesWritten = 0L

        context.contentResolver.openOutputStream(destinationUri)?.let { BufferedOutputStream(it, 64 * 1024) }?.use { output ->
          blobHandle.openInputStream()?.let { BufferedInputStream(it, 64 * 1024) }?.use { bis ->
            val header = ByteArray(4)
            bis.mark(4)
            val headerRead = bis.read(header)
            val isChunked = headerRead == 4 && header.contentEquals(CHUNK_MAGIC)

            if (isChunked && dekKey != null && contentIvBytes != null) {
              var chunkIndex = 0L
              val lenBuffer = ByteArray(4)

              while (true) {
                var lenRead = 0
                while (lenRead < 4) {
                  val r = bis.read(lenBuffer, lenRead, 4 - lenRead)
                  if (r == -1) break
                  lenRead += r
                }
                if (lenRead < 4) break // EOF

                val cipherLen = ((lenBuffer[0].toInt() and 0xFF) shl 24) or
                                ((lenBuffer[1].toInt() and 0xFF) shl 16) or
                                ((lenBuffer[2].toInt() and 0xFF) shl 8) or
                                (lenBuffer[3].toInt() and 0xFF)

                if (cipherLen <= 0 || cipherLen > CHUNK_SIZE + 1024) {
                  throw IllegalStateException("Corrupt chunk length: $cipherLen")
                }

                val cipherBuffer = ByteArray(cipherLen)
                var cipherRead = 0
                while (cipherRead < cipherLen) {
                  val r = bis.read(cipherBuffer, cipherRead, cipherLen - cipherRead)
                  if (r == -1) throw java.io.EOFException("Unexpected EOF reading cipher chunk")
                  cipherRead += r
                }

                val chunkIv = deriveChunkIv(contentIvBytes, chunkIndex)
                val chunkCipher = Cipher.getInstance(DEK_TRANSFORMATION)
                chunkCipher.init(Cipher.DECRYPT_MODE, dekKey, GCMParameterSpec(GCM_TAG_LENGTH, chunkIv))
                val plainBytes = chunkCipher.doFinal(cipherBuffer)

                output.write(plainBytes)
                totalBytesWritten += plainBytes.size
                chunkIndex++
                onProgress(totalBytesWritten, item.fileSizeBytes)
              }
            } else {
              // Legacy non-chunked fallback
              bis.reset()
              val actualStreamCipher: Cipher = legacyStreamCipher ?: run {
                val spec = GCMParameterSpec(GCM_TAG_LENGTH, contentIvBytes!!)
                Cipher.getInstance(DEK_TRANSFORMATION).apply {
                  init(Cipher.DECRYPT_MODE, dekKey!!, spec)
                }
              }
              CipherInputStream(bis, actualStreamCipher).use { cis ->
                val buffer = ByteArray(64 * 1024)
                var bytesRead: Int
                while (cis.read(buffer).also { bytesRead = it } != -1) {
                  output.write(buffer, 0, bytesRead)
                  totalBytesWritten += bytesRead
                  onProgress(totalBytesWritten, item.fileSizeBytes)
                }
              }
            }
            output.flush()
          } ?: return@withContext Result.failure(IllegalStateException("Could not open input stream from blob"))
        } ?: return@withContext Result.failure(IllegalStateException("Could not open destination output stream"))

        CryptoLogger.success("EXPORT_COMPLETE", "Successfully decrypted & exported ${item.originalFileName} ($totalBytesWritten bytes)")
        Log.i(TAG, "Successfully exported file ${item.originalFileName} ($totalBytesWritten bytes)")
        Result.success(Unit)
      } finally {
        unwrappedDekBytesToWipe?.fill(0)
        CryptoLogger.hardware("ZEROIZE", "Unwrapped DEK zeroized from volatile memory.")
      }
    } catch (e: Throwable) {
      CryptoLogger.error("EXPORT_FAIL", "Export failed: ${e.message}")
      Log.e(TAG, "Export failed for item ${item.id}: ${e.message}", e)
      Result.failure(e)
    }
  }

  suspend fun decryptFile(
    item: SecureFileItem,
    cipher: Cipher,
    maxPreviewSizeBytes: Long = MAX_PREVIEW_SIZE_BYTES
  ): Result<DecryptionResult> = withContext(Dispatchers.IO) {
    try {
      CryptoLogger.info("PREVIEW_DECRYPT", "Authorizing in-memory decryption for preview: '${item.originalFileName}'")
      val blobHandle = findBlobHandle(item.encryptedBlobPath)
      if (blobHandle == null || !blobHandle.exists) {
        CryptoLogger.error("PREVIEW_FAIL", "Encrypted blob file does not exist on disk")
        return@withContext Result.failure(IllegalStateException("Encrypted blob file does not exist on disk"))
      }

      if (item.fileSizeBytes > maxPreviewSizeBytes) {
        CryptoLogger.warn("PREVIEW_LIMIT", "File size (${item.fileSizeBytes} B) exceeds max preview threshold (80MB).")
        return@withContext Result.success(DecryptionResult.TooLargeToPreview(item.fileSizeBytes))
      }

      var unwrappedDekBytesToWipe: ByteArray? = null

      try {
        val dekKey: SecretKeySpec?
        val contentIvBytes: ByteArray?
        val legacyStreamCipher: Cipher?

        if (item.wrappedDek.isNotEmpty()) {
          CryptoLogger.hardware("KEYSTORE_UNWRAP", "Unwrapping DEK with hardware Keystore...")
          val wrappedDekBytes = Base64.decode(item.wrappedDek, Base64.NO_WRAP)
          val unwrappedDekBytes = cipher.doFinal(wrappedDekBytes)
          unwrappedDekBytesToWipe = unwrappedDekBytes
          dekKey = SecretKeySpec(unwrappedDekBytes, "AES")
          contentIvBytes = Base64.decode(item.iv, Base64.NO_WRAP)
          legacyStreamCipher = null
        } else {
          dekKey = null
          contentIvBytes = null
          legacyStreamCipher = cipher
        }

        val baos = ByteArrayOutputStream()
        var isOversized = false
        var totalRead = 0L

        blobHandle.openInputStream()?.let { BufferedInputStream(it, 64 * 1024) }?.use { bis ->
          val header = ByteArray(4)
          bis.mark(4)
          val headerRead = bis.read(header)
          val isChunked = headerRead == 4 && header.contentEquals(CHUNK_MAGIC)

          if (isChunked && dekKey != null && contentIvBytes != null) {
            var chunkIndex = 0L
            val lenBuffer = ByteArray(4)

            while (true) {
              var lenRead = 0
              while (lenRead < 4) {
                val r = bis.read(lenBuffer, lenRead, 4 - lenRead)
                if (r == -1) break
                lenRead += r
              }
              if (lenRead < 4) break // EOF

              val cipherLen = ((lenBuffer[0].toInt() and 0xFF) shl 24) or
                              ((lenBuffer[1].toInt() and 0xFF) shl 16) or
                              ((lenBuffer[2].toInt() and 0xFF) shl 8) or
                              (lenBuffer[3].toInt() and 0xFF)

              if (cipherLen <= 0 || cipherLen > CHUNK_SIZE + 1024) {
                throw IllegalStateException("Corrupt chunk length: $cipherLen")
              }

              val cipherBuffer = ByteArray(cipherLen)
              var cipherRead = 0
              while (cipherRead < cipherLen) {
                val r = bis.read(cipherBuffer, cipherRead, cipherLen - cipherRead)
                if (r == -1) throw java.io.EOFException("Unexpected EOF reading chunk")
                cipherRead += r
              }

              val chunkIv = deriveChunkIv(contentIvBytes, chunkIndex)
              val chunkCipher = Cipher.getInstance(DEK_TRANSFORMATION)
              chunkCipher.init(Cipher.DECRYPT_MODE, dekKey, GCMParameterSpec(GCM_TAG_LENGTH, chunkIv))
              val plainBytes = chunkCipher.doFinal(cipherBuffer)

              totalRead += plainBytes.size
              if (totalRead > maxPreviewSizeBytes) {
                isOversized = true
                break
              }
              baos.write(plainBytes)
              chunkIndex++
            }
          } else {
            // Legacy fallback
            bis.reset()
            val actualStreamCipher: Cipher = legacyStreamCipher ?: run {
              val spec = GCMParameterSpec(GCM_TAG_LENGTH, contentIvBytes!!)
              Cipher.getInstance(DEK_TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, dekKey!!, spec)
              }
            }
            CipherInputStream(bis, actualStreamCipher).use { cis ->
              val buffer = ByteArray(64 * 1024)
              var bytesRead: Int
              while (cis.read(buffer).also { bytesRead = it } != -1) {
                totalRead += bytesRead
                if (totalRead > maxPreviewSizeBytes) {
                  isOversized = true
                  break
                }
                baos.write(buffer, 0, bytesRead)
              }
            }
          }
        } ?: return@withContext Result.failure(IllegalStateException("Could not open input stream from blob"))

        if (isOversized) {
          val reportedSize = if (item.fileSizeBytes > 0) item.fileSizeBytes else totalRead
          CryptoLogger.warn("PREVIEW_LIMIT", "Oversized file ($reportedSize bytes). Preview truncated.")
          Result.success(DecryptionResult.TooLargeToPreview(reportedSize))
        } else {
          CryptoLogger.success("PREVIEW_READY", "Plaintext rendered into RAM buffer (${baos.size()} bytes). Zeroized on dismiss.")
          Result.success(DecryptionResult.Success(baos.toByteArray()))
        }
      } finally {
        unwrappedDekBytesToWipe?.fill(0)
        CryptoLogger.hardware("ZEROIZE", "Plaintext key wiped from heap.")
      }
    } catch (e: Throwable) {
      CryptoLogger.error("PREVIEW_FAIL", "Decryption error: ${e.message}")
      Log.e(TAG, "Decryption failed for item ${item.id}: ${e.message}", e)
      Result.failure(e)
    }
  }

  suspend fun deleteFile(item: SecureFileItem) = withContext(Dispatchers.IO) {
    try {
      val blobHandle = findBlobHandle(item.encryptedBlobPath)
      if (blobHandle != null && blobHandle.exists) {
        blobHandle.delete()
      }
      dao.deleteFile(item)
      SecureVaultMetadataSafety.recordShadowSnapshot(context, dao.getAllFilesSync())
      Log.i(TAG, "Deleted file ${item.id} and removed encrypted blob.")
    } catch (e: Throwable) {
      Log.e(TAG, "Error deleting file ${item.id}: ${e.message}", e)
    }
  }

  suspend fun migrateStorage(
    targetLocation: VaultStorageLocation,
    onProgress: (migratedCount: Int, totalCount: Int) -> Unit = { _, _ -> }
  ): Result<Int> = withContext(Dispatchers.IO) {
    try {
      val allItems = dao.getAllFilesSync()
      var migrated = 0

      val customTree = if (targetLocation == VaultStorageLocation.PERSISTENT_CUSTOM_FOLDER) {
        getCustomFolderDocument() ?: throw IllegalStateException("No valid custom folder selected via SAF")
      } else null

      for ((index, item) in allItems.withIndex()) {
        val existingHandle = findBlobHandle(item.encryptedBlobPath)
        if (existingHandle != null && existingHandle.exists) {
          val buffer = ByteArray(64 * 1024)
          var writeSuccess = false

          if (targetLocation == VaultStorageLocation.PERSISTENT_CUSTOM_FOLDER) {
            val createdDoc = customTree!!.createFile("application/octet-stream", item.encryptedBlobPath)
            if (createdDoc != null) {
              context.contentResolver.openOutputStream(createdDoc.uri)?.use { out ->
                existingHandle.openInputStream()?.use { inp ->
                  var read: Int
                  while (inp.read(buffer).also { read = it } != -1) {
                    out.write(buffer, 0, read)
                  }
                }
              }
              if (createdDoc.length() == existingHandle.length()) {
                writeSuccess = true
              }
            }
          } else {
            val destDir = getVaultDir(targetLocation)
            val destFile = File(destDir, item.encryptedBlobPath)
            FileOutputStream(destFile).use { out ->
              existingHandle.openInputStream()?.use { inp ->
                var read: Int
                while (inp.read(buffer).also { read = it } != -1) {
                  out.write(buffer, 0, read)
                }
              }
            }
            if (destFile.exists() && destFile.length() == existingHandle.length()) {
              writeSuccess = true
            }
          }

          if (writeSuccess) {
            existingHandle.delete()
            migrated++
          }
        }
        onProgress(index + 1, allItems.size)
      }

      storagePreferences.setStorageLocation(targetLocation)
      Log.i(TAG, "Migrated $migrated files to ${targetLocation.title}")
      Result.success(migrated)
    } catch (e: Throwable) {
      Log.e(TAG, "Migration failed: ${e.message}", e)
      Result.failure(e)
    }
  }

  suspend fun exportVaultBackup(
    destinationUri: Uri,
    onProgress: (exportedCount: Int, totalCount: Int) -> Unit = { _, _ -> }
  ): Result<Int> = withContext(Dispatchers.IO) {
    try {
      val allItems = dao.getAllFilesSync()
      val manifestArray = JSONArray()

      for (item in allItems) {
        val obj = JSONObject().apply {
          put("id", item.id)
          put("originalFileName", item.originalFileName)
          put("mimeType", item.mimeType)
          put("fileSizeBytes", item.fileSizeBytes)
          put("encryptedBlobPath", item.encryptedBlobPath)
          put("dateAdded", item.dateAdded)
          put("iv", item.iv)
          put("wrappedDek", item.wrappedDek)
          put("dekIv", item.dekIv)
        }
        manifestArray.put(obj)
      }

      val manifestJsonBytes = manifestArray.toString().toByteArray(Charsets.UTF_8)

      context.contentResolver.openOutputStream(destinationUri)?.let { BufferedOutputStream(it, 64 * 1024) }?.use { out ->
        // 1. Write Header Magic
        out.write(BACKUP_MAGIC)

        // 2. Write Manifest JSON Length & Data
        val manifestLen = manifestJsonBytes.size
        out.write((manifestLen ushr 24) and 0xFF)
        out.write((manifestLen ushr 16) and 0xFF)
        out.write((manifestLen ushr 8) and 0xFF)
        out.write(manifestLen and 0xFF)
        out.write(manifestJsonBytes)

        // 3. Write all encrypted blobs
        val buffer = ByteArray(64 * 1024)
        for ((index, item) in allItems.withIndex()) {
          val blobHandle = findBlobHandle(item.encryptedBlobPath)
          val blobLength = blobHandle?.length() ?: 0L

          val nameBytes = item.encryptedBlobPath.toByteArray(Charsets.UTF_8)
          out.write(nameBytes.size and 0xFF)
          out.write(nameBytes)

          out.write(((blobLength ushr 56) and 0xFF).toInt())
          out.write(((blobLength ushr 48) and 0xFF).toInt())
          out.write(((blobLength ushr 40) and 0xFF).toInt())
          out.write(((blobLength ushr 32) and 0xFF).toInt())
          out.write(((blobLength ushr 24) and 0xFF).toInt())
          out.write(((blobLength ushr 16) and 0xFF).toInt())
          out.write(((blobLength ushr 8) and 0xFF).toInt())
          out.write((blobLength and 0xFF).toInt())

          if (blobHandle != null && blobHandle.exists) {
            blobHandle.openInputStream()?.use { fis ->
              var read: Int
              while (fis.read(buffer).also { read = it } != -1) {
                out.write(buffer, 0, read)
              }
            }
          }

          onProgress(index + 1, allItems.size)
        }
        out.flush()
      } ?: return@withContext Result.failure(IllegalStateException("Could not open destination stream for backup"))

      Log.i(TAG, "Vault backup exported successfully with ${allItems.size} files.")
      Result.success(allItems.size)
    } catch (e: Throwable) {
      Log.e(TAG, "Failed to export vault backup: ${e.message}", e)
      Result.failure(e)
    }
  }

  suspend fun restoreVaultBackup(
    sourceUri: Uri,
    onProgress: (restoredCount: Int, totalCount: Int) -> Unit = { _, _ -> }
  ): Result<Int> = withContext(Dispatchers.IO) {
    try {
      val activeLoc = storagePreferences.getStorageLocation()
      val customTree = if (activeLoc == VaultStorageLocation.PERSISTENT_CUSTOM_FOLDER) getCustomFolderDocument() else null
      val targetDir = getVaultDir(activeLoc)
      var restoredCount = 0

      context.contentResolver.openInputStream(sourceUri)?.let { BufferedInputStream(it, 64 * 1024) }?.use { input ->
        val magic = ByteArray(4)
        var mRead = 0
        while (mRead < 4) {
          val r = input.read(magic, mRead, 4 - mRead)
          if (r == -1) break
          mRead += r
        }
        if (mRead < 4 || !magic.contentEquals(BACKUP_MAGIC)) {
          return@withContext Result.failure(IllegalArgumentException("Invalid or unrecognized SecureVault backup file format"))
        }

        // Read manifest length
        val lenBuf = ByteArray(4)
        var lRead = 0
        while (lRead < 4) {
          val r = input.read(lenBuf, lRead, 4 - lRead)
          if (r == -1) break
          lRead += r
        }
        val manifestLen = ((lenBuf[0].toInt() and 0xFF) shl 24) or
                          ((lenBuf[1].toInt() and 0xFF) shl 16) or
                          ((lenBuf[2].toInt() and 0xFF) shl 8) or
                          (lenBuf[3].toInt() and 0xFF)

        if (manifestLen <= 0 || manifestLen > 10 * 1024 * 1024) {
          return@withContext Result.failure(IllegalArgumentException("Backup archive manifest corrupt or too large"))
        }

        val manifestBytes = ByteArray(manifestLen)
        var mfRead = 0
        while (mfRead < manifestLen) {
          val r = input.read(manifestBytes, mfRead, manifestLen - mfRead)
          if (r == -1) break
          mfRead += r
        }

        val manifestString = String(manifestBytes, Charsets.UTF_8)
        val manifestArray = JSONArray(manifestString)
        val totalEntries = manifestArray.length()

        val itemsMap = mutableMapOf<String, JSONObject>()
        for (i in 0 until totalEntries) {
          val obj = manifestArray.getJSONObject(i)
          itemsMap[obj.getString("encryptedBlobPath")] = obj
        }

        val buffer = ByteArray(64 * 1024)

        for (i in 0 until totalEntries) {
          val nameLen = input.read()
          if (nameLen == -1) break
          val nameBytes = ByteArray(nameLen)
          var nRead = 0
          while (nRead < nameLen) {
            val r = input.read(nameBytes, nRead, nameLen - nRead)
            if (r == -1) break
            nRead += r
          }
          val blobName = String(nameBytes, Charsets.UTF_8)

          val sizeBuf = ByteArray(8)
          var sRead = 0
          while (sRead < 8) {
            val r = input.read(sizeBuf, sRead, 8 - sRead)
            if (r == -1) break
            sRead += r
          }

          var blobLen = 0L
          for (b in 0 until 8) {
            blobLen = (blobLen shl 8) or (sizeBuf[b].toLong() and 0xFF)
          }

          var bytesRestoredForBlob = 0L
          val outStream: OutputStream = if (customTree != null) {
            val doc = customTree.createFile("application/octet-stream", blobName)
              ?: throw IllegalStateException("Failed to create restored blob doc in custom folder")
            context.contentResolver.openOutputStream(doc.uri)
              ?: throw IllegalStateException("Failed to open output stream for custom doc")
          } else {
            val targetBlobFile = File(targetDir, blobName)
            FileOutputStream(targetBlobFile)
          }

          outStream.use { fos ->
            var remaining = blobLen
            while (remaining > 0) {
              val toRead = remaining.coerceAtMost(buffer.size.toLong()).toInt()
              val r = input.read(buffer, 0, toRead)
              if (r == -1) break
              fos.write(buffer, 0, r)
              remaining -= r
              bytesRestoredForBlob += r
            }
          }

          val jsonMeta = itemsMap[blobName]
          if (jsonMeta != null) {
            val item = SecureFileItem(
              originalFileName = jsonMeta.optString("originalFileName", "restored_file"),
              mimeType = jsonMeta.optString("mimeType", "application/octet-stream"),
              fileSizeBytes = jsonMeta.optLong("fileSizeBytes", bytesRestoredForBlob),
              encryptedBlobPath = blobName,
              dateAdded = jsonMeta.optLong("dateAdded", System.currentTimeMillis()),
              iv = jsonMeta.optString("iv", ""),
              wrappedDek = jsonMeta.optString("wrappedDek", ""),
              dekIv = jsonMeta.optString("dekIv", "")
            )
            dao.insertFile(item)
            restoredCount++
          }

          onProgress(i + 1, totalEntries)
        }

        if (restoredCount > 0) {
          SecureVaultMetadataSafety.recordShadowSnapshot(context, dao.getAllFilesSync())
        }
        Result.success(restoredCount)
      } ?: Result.failure(IllegalStateException("Could not open backup file"))
    } catch (e: Throwable) {
      Log.e(TAG, "Failed to restore vault backup: ${e.message}", e)
      Result.failure(e)
    }
  }

  suspend fun verifyIntegrityAndSelfHeal() = withContext(Dispatchers.IO) {
    SecureVaultMetadataSafety.verifyAndSelfHeal(dao, context, this@SecureVaultRepository)
  }
}

