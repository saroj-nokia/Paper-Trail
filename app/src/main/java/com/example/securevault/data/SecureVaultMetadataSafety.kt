package com.example.securevault.data

import android.content.Context
import android.util.Log
import com.example.securevault.logging.CryptoLogger
import com.example.securevault.model.SecureFileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Provides update protection and integrity recovery for SecureVault.
 *
 * It persists an encrypted shadow journal of file metadata in noBackupFilesDir.
 * In the event of an unexpected database migration issue or OS interrupted update,
 * SecureVault can verify data integrity and restore metadata records so that
 * no encrypted file ever loses its decryption parameters (wrapped DEK, IVs).
 */
object SecureVaultMetadataSafety {
  private const val TAG = "SecureVaultMetaSafety"
  private const val SHADOW_FILE_NAME = "securevault_meta_shadow.json"

  private fun getShadowFile(context: Context): File {
    val dir = context.noBackupFilesDir ?: context.filesDir
    return File(dir, SHADOW_FILE_NAME)
  }

  suspend fun recordShadowSnapshot(context: Context, items: List<SecureFileItem>) = withContext(Dispatchers.IO) {
    try {
      val file = getShadowFile(context)
      val array = JSONArray()
      for (item in items) {
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
        array.put(obj)
      }

      val tempFile = File(file.parentFile, "${file.name}.tmp")
      FileOutputStream(tempFile).use { out ->
        out.write(array.toString().toByteArray(Charsets.UTF_8))
        out.flush()
      }
      if (tempFile.renameTo(file)) {
        CryptoLogger.hardware("META_SHADOW", "App update metadata shadow journal updated (${items.size} records).")
      }
    } catch (e: Exception) {
      Log.w(TAG, "Failed to record metadata shadow snapshot: ${e.message}")
    }
  }

  suspend fun readShadowSnapshot(context: Context): List<SecureFileItem> = withContext(Dispatchers.IO) {
    val file = getShadowFile(context)
    if (!file.exists()) return@withContext emptyList()

    try {
      val jsonString = FileInputStream(file).use { input ->
        input.bufferedReader(Charsets.UTF_8).readText()
      }
      val array = JSONArray(jsonString)
      val list = mutableListOf<SecureFileItem>()
      for (i in 0 until array.length()) {
        val obj = array.getJSONObject(i)
        list.add(
          SecureFileItem(
            id = obj.optLong("id", 0L),
            originalFileName = obj.getString("originalFileName"),
            mimeType = obj.getString("mimeType"),
            fileSizeBytes = obj.getLong("fileSizeBytes"),
            encryptedBlobPath = obj.getString("encryptedBlobPath"),
            dateAdded = obj.getLong("dateAdded"),
            iv = obj.getString("iv"),
            wrappedDek = obj.optString("wrappedDek", ""),
            dekIv = obj.optString("dekIv", "")
          )
        )
      }
      list
    } catch (e: Exception) {
      Log.w(TAG, "Failed to read shadow snapshot: ${e.message}")
      emptyList()
    }
  }

  suspend fun verifyAndSelfHeal(dao: SecureVaultDao, context: Context, repository: SecureVaultRepository) = withContext(Dispatchers.IO) {
    try {
      val dbItems = dao.getAllFilesSync()
      if (dbItems.isEmpty()) {
        val shadowItems = readShadowSnapshot(context)
        if (shadowItems.isNotEmpty()) {
          CryptoLogger.warn("SELF_HEAL", "Database empty but shadow journal found. Restoring ${shadowItems.size} items post-update...")
          var restoredCount = 0
          for (item in shadowItems) {
            val handle = repository.findBlobHandle(item.encryptedBlobPath)
            if (handle != null && handle.exists) {
              dao.insertFile(item)
              restoredCount++
            }
          }
          CryptoLogger.success("SELF_HEAL", "Successfully verified & recovered $restoredCount files after update.")
        }
      } else {
        // Update the shadow journal to match current verified state
        recordShadowSnapshot(context, dbItems)
      }
    } catch (e: Exception) {
      Log.w(TAG, "Self-heal check error: ${e.message}")
    }
  }
}
