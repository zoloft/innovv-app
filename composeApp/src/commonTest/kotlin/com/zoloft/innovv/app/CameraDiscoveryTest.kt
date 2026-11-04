package com.zoloft.innovv.app

import com.zoloft.innovv.app.domain.isInnovvSsid
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CameraDiscoveryTest {
    @Test
    fun matchesDynamicInnovvNames() {
        assertTrue(isInnovvSsid("5G_INNOVV_K5_1234"))
        assertTrue(isInnovvSsid("INNOVV_K7_5.8G"))
        assertTrue(isInnovvSsid("innovv-k5-abc"))
        assertFalse(isInnovvSsid("HomeWiFi"))
        assertFalse(isInnovvSsid("K5_1234"))
    }
}
