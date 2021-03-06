package org.zimmob.zimlx.globalsearch.providers

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import androidx.annotation.Keep
import com.android.launcher3.R
import com.android.launcher3.util.PackageManagerHelper
import org.zimmob.zimlx.globalsearch.SearchProvider

@Keep
class GoogleGoSearchProvider(context: Context) : SearchProvider(context) {

    override val name = context.getString(R.string.search_provider_google_go)
    override val supportsVoiceSearch = true
    override val supportsAssistant = false
    override val supportsFeed = true
    override val isAvailable: Boolean
        get() = PackageManagerHelper.isAppEnabled(context.packageManager, PACKAGE, 0)

    override fun startSearch(callback: (intent: Intent) -> Unit) = callback(Intent("$PACKAGE.SEARCH").putExtra("showKeyboard", true).putExtra("$PACKAGE.SKIP_BYPASS_AND_ONBOARDING", true).setPackage(PACKAGE))
    override fun startVoiceSearch(callback: (intent: Intent) -> Unit) = callback(Intent("$PACKAGE.SEARCH").putExtra("openMic", true).putExtra("$PACKAGE.SKIP_BYPASS_AND_ONBOARDING", true).setPackage(PACKAGE))
    override fun startFeed(callback: (intent: Intent) -> Unit) = callback(Intent("$PACKAGE.SEARCH").putExtra("$PACKAGE.SKIP_BYPASS_AND_ONBOARDING", true).setPackage(PACKAGE))

    override fun getIcon(): Drawable = context.getDrawable(R.drawable.ic_qsb_logo)!!

    override fun getVoiceIcon(): Drawable = context.getDrawable(R.drawable.ic_qsb_mic)!!

    companion object {
        private const val PACKAGE = "com.google.android.apps.searchlite"
    }
}