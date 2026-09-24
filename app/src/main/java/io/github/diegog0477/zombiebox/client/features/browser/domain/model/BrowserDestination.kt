package io.github.diegog0477.zombiebox.client.features.browser.domain.model

import java.net.URLEncoder

/** Converts the TV address bar into a web address or a Google search. */
object BrowserDestination {
    fun resolve(value: String): String {
        val input = value.trim().take(2048)
        if (input.isEmpty()) return "https://www.google.com/"
        if (input.startsWith("https://", true) || input.startsWith("http://", true)) {
            return input
        }
        if (!input.contains(' ') && !input.contains('\n') && looksLikeHost(input)) {
            return "https://$input"
        }
        return "https://www.google.com/search?q=" + URLEncoder.encode(input, "UTF-8")
    }

    private fun looksLikeHost(input: String): Boolean {
        val host = input.substringBefore('/').substringBefore(':')
        return host == "localhost" || host.matches(Regex("[a-zA-Z0-9-]+(\\.[a-zA-Z0-9-]+)+"))
    }
}
