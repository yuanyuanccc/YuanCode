package com.yuancode.terminal;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TerminalCapabilitiesTest {
    @Test
    void carriesPlainTerminalFallbackInformation() {
        TerminalCapabilities capabilities = new TerminalCapabilities(false, false, false, 80);
        assertFalse(capabilities.interactive());
        assertFalse(capabilities.ansi());
        assertEquals(80, capabilities.width());
    }
}
