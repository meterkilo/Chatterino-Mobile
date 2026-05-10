package com.example.chatterinomobile.ui.player

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

@SuppressLint("SetJavaScriptEnabled")
class TwitchStreamWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.webViewStyle
) : WebView(context, attrs, defStyleAttr) {

    init {
        setBackgroundColor(Color.BLACK)
        overScrollMode = OVER_SCROLL_NEVER
        isHorizontalScrollBarEnabled = false
        isVerticalScrollBarEnabled = false

        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.setSupportZoom(false)

        webChromeClient = WebChromeClient()
        webViewClient = GuardedTwitchWebViewClient()
    }
}

class GuardedTwitchWebViewClient(
    private val onPageFinished: (() -> Unit)? = null,
    private val onError: (() -> Unit)? = null
) : WebViewClient() {

    override fun onPageFinished(view: WebView?, url: String?) {
        if (url != null && url != BLANK_URL) onPageFinished?.invoke()
    }

    @Deprecated("Deprecated in Java")
    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean =
        shouldBlock(url)

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean =
        shouldBlock(request?.url?.toString())

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: android.webkit.WebResourceError?
    ) {
        if (request?.isForMainFrame == true) onError?.invoke()
    }

    private fun shouldBlock(url: String?): Boolean {
        if (url.isNullOrBlank()) return true
        return ALLOWED_PATHS.none { url.startsWith(it) }
    }

    private companion object {
        const val BLANK_URL = "about:blank"
        val ALLOWED_PATHS = listOf(
            BLANK_URL,
            "https://id.twitch.tv/",
            "https://www.twitch.tv/passport-callback",
            "https://player.twitch.tv/"
        )
    }
}

