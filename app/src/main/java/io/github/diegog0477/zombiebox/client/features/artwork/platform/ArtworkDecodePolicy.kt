package io.github.diegog0477.zombiebox.client.features.artwork.platform

import io.github.diegog0477.zombiebox.client.features.artwork.domain.repository.ArtworkRequestRole

internal fun artworkSampleSize(
    width: Int,
    height: Int,
    targetEdge: Int,
    role: ArtworkRequestRole,
): Int {
    require(width > 0 && height > 0 && targetEdge > 0)
    var sampleSize = 1
    while (
        width / sampleSize > targetEdge ||
            (role == ArtworkRequestRole.AUDIO && height / sampleSize > targetEdge)
    ) {
        sampleSize *= 2
    }
    return sampleSize
}
