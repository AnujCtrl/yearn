package com.anujp.yearn

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.anujp.yearn.capture.ShareActivity
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ShareTargetManifestTest {
    private fun resolves(action: String, type: String) =
        ApplicationProvider.getApplicationContext<Context>().packageManager
            .queryIntentActivities(Intent(action).setType(type), 0)
            .any { it.activityInfo.name == ShareActivity::class.java.name }

    @Test fun acceptsTextImageVideoAndAnything() {
        assertTrue(resolves(Intent.ACTION_SEND, "text/plain"))
        assertTrue(resolves(Intent.ACTION_SEND, "video/mp4"))
        assertTrue(resolves(Intent.ACTION_SEND_MULTIPLE, "image/*"))
        assertTrue(resolves(Intent.ACTION_SEND, "application/x-unknown"))
    }
}
