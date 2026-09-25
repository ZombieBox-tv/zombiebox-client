package io.github.diegog0477.zombiebox.client.features.playback.presentation.ui

/** Keeps the video controls and lower content panel separate across async metadata refreshes. */
internal class PlayerChromeSections {
    private var itemId: String? = null
    var relatedAvailable = false
        private set

    var detailsAvailable = false
        private set

    var expanded = false
        private set

    fun bind(id: String?, related: Boolean, details: Boolean) {
        if (itemId != id) expanded = false
        itemId = id
        relatedAvailable = related
        detailsAvailable = details
        if (!related && !details) expanded = false
    }

    fun expand(): Boolean {
        if (!relatedAvailable && !detailsAvailable) return false
        expanded = true
        return true
    }

    fun collapse(): Boolean {
        if (!expanded) return false
        expanded = false
        return true
    }
}
