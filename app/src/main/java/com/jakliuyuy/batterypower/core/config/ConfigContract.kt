package com.jakliuyuy.batterypower.core.config

/**
 * Cross-process configuration contract (spec sections 40, 41).
 * The provider is read-only: no external caller may modify configuration.
 */
object ConfigContract {

    const val AUTHORITY_SUFFIX = ".config"

    const val PATH_VERSION = "version"
    const val PATH_CONFIG = "config"

    const val COL_KEY = "key"
    const val COL_VALUE = "value"
    const val COL_TYPE = "type"
    const val COL_VERSION = "version"
    const val COL_UPDATED_AT = "updated_at"

    fun authorityFor(packageName: String): String = "$packageName$AUTHORITY_SUFFIX"

    fun buildVersionUri(authority: String): android.net.Uri =
        android.net.Uri.parse("content://$authority/$PATH_VERSION")

    fun buildConfigUri(authority: String): android.net.Uri =
        android.net.Uri.parse("content://$authority/$PATH_CONFIG")
}
