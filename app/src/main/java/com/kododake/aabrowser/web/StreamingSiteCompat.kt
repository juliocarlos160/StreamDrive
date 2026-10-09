/*
 * Copyright (C) 2025 AABrowser Contributors (https://github.com/kododake/AABrowser)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://gnu.org>.
 */

package com.kododake.aabrowser.web

import android.net.Uri
import android.webkit.WebView
import com.kododake.aabrowser.R
import com.kododake.aabrowser.data.BrowserPreferences
import com.kododake.aabrowser.model.UserAgentProfile

/**
 * Makes streaming services that only offer playback on their desktop sites work in the browser.
 *
 * Prime Video and Disney+ send Android browsers to the Play Store instead of playing video, but
 * their desktop sites play through Widevine L3. While a tab is on one of these services, it uses the
 * desktop Chrome identity regardless of the user's settings; once the tab leaves the service (and its
 * sign-in pages), the user's own identity is restored.
 */
object StreamingSiteCompat {

    /** Services whose video only plays on the desktop site. */
    private val DESKTOP_STREAMING_DOMAINS = listOf(
        "primevideo.com",
        "disneyplus.com",
        "disney-plus.net",
        "bamgrid.com"
    )

    /** Sign-in and account pages these services send you through; the desktop identity is kept there. */
    private val RELATED_DOMAINS = listOf(
        "disney.com",
        "go.com",
        "mydisney.com"
    )
    private val AMAZON_HOST = Regex("""(^|\.)amazon\.[a-z]{2,3}(\.[a-z]{2})?$""")

    private val STREAMING_PROFILE = UserAgentProfile.ANDROID_CHROME

    fun isDesktopStreamingHost(host: String): Boolean = DESKTOP_STREAMING_DOMAINS.any { host.matchesDomain(it) }

    private fun isRelatedHost(host: String): Boolean =
        AMAZON_HOST.containsMatchIn(host) || RELATED_DOMAINS.any { host.matchesDomain(it) }

    private fun String.matchesDomain(domain: String): Boolean = this == domain || endsWith(".$domain")

    /**
     * Switches the WebView's browser identity to the one [uri] needs.
     *
     * @return true when the identity changed, meaning the caller must (re)load [uri] so the
     * server receives the new user agent.
     */
    fun applyIdentityFor(view: WebView, uri: Uri?): Boolean {
        val scheme = uri?.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return false
        val host = uri?.host?.lowercase() ?: return false

        val wasStreaming = view.getTag(R.id.webview_streaming_identity_tag) == true
        val streaming = isDesktopStreamingHost(host) || (wasStreaming && isRelatedHost(host))
        view.setTag(R.id.webview_streaming_identity_tag, streaming)

        // Outside streaming sites, leave the user's identity alone unless we changed it earlier.
        if (!streaming && !wasStreaming) return false

        val context = view.context
        val profile = if (streaming) STREAMING_PROFILE else BrowserPreferences.getUserAgentProfile(context)
        val desktop = streaming || BrowserPreferences.shouldUseDesktopMode(context)
        if (UserAgentManager.isBrowserIdentityApplied(view, profile, desktop)) return false

        UserAgentManager.applyBrowserIdentity(view, profile, desktop)
        return true
    }
}
