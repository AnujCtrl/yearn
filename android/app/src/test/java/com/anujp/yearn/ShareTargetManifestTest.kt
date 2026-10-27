package com.anujp.yearn

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.anujp.yearn.capture.ShareActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
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

    @Test fun launcherAndShareSheetSayYearnWithAnIcon() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val pm = ctx.packageManager
        assertEquals("yearn", ctx.getString(R.string.app_name))
        assertEquals("yearn", ctx.applicationInfo.loadLabel(pm).toString())
        val share = pm.queryIntentActivities(Intent(Intent.ACTION_SEND).setType("text/plain"), 0)
            .single { it.activityInfo.name == ShareActivity::class.java.name }
        assertEquals("yearn", share.loadLabel(pm).toString())
        val launcher = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(ctx.packageName), 0,
        ).single()
        assertEquals("yearn", launcher.loadLabel(pm).toString())
        assertNotEquals(0, ctx.applicationInfo.icon)
        assertNotNull(ctx.getDrawable(ctx.applicationInfo.icon))
    }
}
