package com.zoloft.innovv.app.domain

/**
 * INNOVV access-point names contain a dynamic suffix, for example
 * `5G_INNOVV_K5_1234` or `INNOVV_K7_5.8G`. Match the stable vendor token
 * instead of hard-coding a complete SSID.
 */
fun isInnovvSsid(ssid: String): Boolean = ssid.contains("INNOVV", ignoreCase = true)
