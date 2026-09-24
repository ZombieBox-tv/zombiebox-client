package io.github.diegog0477.zombiebox.client

import io.github.diegog0477.zombiebox.client.features.browser.domain.model.BrowserDestination
import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserDestinationTest {
    @Test
    fun searchAndAddressResolveWithoutTreatingTextAsCommands() {
        assertEquals("https://www.google.com/", BrowserDestination.resolve(" "))
        assertEquals("https://example.org/watch", BrowserDestination.resolve("example.org/watch"))
        assertEquals("http://localhost:8080/", BrowserDestination.resolve("http://localhost:8080/"))
        assertEquals(
            "https://www.google.com/search?q=films+in+Mexico",
            BrowserDestination.resolve("films in Mexico"),
        )
        assertEquals(
            "https://www.google.com/search?q=javascript%3Aalert%281%29",
            BrowserDestination.resolve("javascript:alert(1)"),
        )
    }
}
