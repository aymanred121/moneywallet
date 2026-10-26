package com.oriondev.moneywallet.next.ui

import androidx.compose.ui.unit.dp
import com.oriondev.moneywallet.next.ui.components.transactions.keySizeFor
import org.junit.Assert.assertEquals
import org.junit.Test

class KeypadSizeTest {
    @Test fun shorterPhonesGetACompactKeypad() {
        assertEquals(48.dp, keySizeFor(834).height) // 1220×2712 @ 520dpi
        assertEquals(56.dp, keySizeFor(914).height) // 1080×2400 @ 420dpi
        assertEquals(42.dp, keySizeFor(640).height)
    }
}
