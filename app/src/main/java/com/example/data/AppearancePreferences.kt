package com.example.data

import android.content.Context

object AppearancePreferences {
  private const val PREFS_NAME = "papertrail_appearance_prefs"
  private const val KEY_FROSTED_GLASS_ENABLED = "frosted_glass_enabled"

  fun isFrostedGlassEnabled(context: Context): Boolean =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .getBoolean(KEY_FROSTED_GLASS_ENABLED, false) // OFF by default

  fun setFrostedGlassEnabled(context: Context, enabled: Boolean) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit().putBoolean(KEY_FROSTED_GLASS_ENABLED, enabled).apply()
  }
}
