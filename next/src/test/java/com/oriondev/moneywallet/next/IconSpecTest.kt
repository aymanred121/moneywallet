package com.oriondev.moneywallet.next

import com.oriondev.moneywallet.next.core.IconSpec
import com.oriondev.moneywallet.next.ui.components.SymbolIcons
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class IconSpecTest {
    @Test fun symbolRoundTrips() {
        val s = IconSpec.Symbol("restaurant", "#E91E63", "RE")
        assertEquals(s, IconSpec.parse(s.encode()))
    }

    @Test fun symbolStaysReadableByTheLegacyApp() {
        // the legacy app only knows "color" icons with a colour and up to two letters
        val o = JSONObject(IconSpec.Symbol("restaurant", "#E91E63", "RE").encode())
        assertEquals("color", o.getString("type"))
        assertEquals("#E91E63", o.getString("color"))
        assertEquals("RE", o.getString("name"))
    }

    @Test fun everyLegacyFallbackAndDefaultHasASymbol() {
        SymbolIcons.legacyNames.values.forEach { assertNotNull(it, SymbolIcons.vector(it)) }
        assertEquals(SymbolIcons.groups.sumOf { it.second.size }, SymbolIcons.groups.flatMap { g -> g.second.map { it.key } }.toSet().size) // keys unique
    }
}
