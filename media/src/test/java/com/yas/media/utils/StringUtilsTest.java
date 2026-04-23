package com.yas.media.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StringUtilsTest {
    
    @Test
    void hasText_Success() {
        assertTrue(StringUtils.hasText("yas"));
        assertFalse(StringUtils.hasText(""));
        assertFalse(StringUtils.hasText("   "));
        assertFalse(StringUtils.hasText(null));
    }
}
