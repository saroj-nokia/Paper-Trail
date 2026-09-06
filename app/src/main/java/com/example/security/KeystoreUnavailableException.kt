package com.example.security

class KeystoreUnavailableException(
  message: String,
  cause: Throwable? = null
) : Exception(message, cause)
