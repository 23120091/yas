package com.yas.sampledata.utils;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ResourceBundle;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessagesUtilsTest {

    @Test
    void getMessage_shouldReturnFormattedMessage_whenKeyExists() throws Exception {
        // Arrange
        ResourceBundle mockBundle = new ResourceBundle() {
            @Override
            protected Object handleGetObject(String key) {
                if (key.equals("test.key")) {
                    return "Hello {}";
                }
                return null;
            }

            @Override
            public boolean containsKey(String key) {
                return key.equals("test.key");
            }

            @Override
            public java.util.Enumeration<String> getKeys() {
                return java.util.Collections.enumeration(java.util.List.of("test.key"));
            }
        };

        injectMockBundle(mockBundle);

        // Act
        String result = MessagesUtils.getMessage("test.key", "World");

        // Assert
        assertEquals("Hello World", result);
    }

    @Test
    void getMessage_shouldReturnErrorCode_whenKeyNotExists() throws Exception {
        // Arrange
        ResourceBundle mockBundle = new ResourceBundle() {
            @Override
            protected Object handleGetObject(String key) {
                throw new java.util.MissingResourceException("", "", key);
            }

            @Override
            public java.util.Enumeration<String> getKeys() {
                return java.util.Collections.emptyEnumeration();
            }
        };

        injectMockBundle(mockBundle);

        // Act
        String result = MessagesUtils.getMessage("unknown.key");

        // Assert
        assertEquals("unknown.key", result);
    }

    // 🔥 Trick quan trọng: inject ResourceBundle vào static field
    private void injectMockBundle(ResourceBundle mockBundle) throws Exception {
        Field field = MessagesUtils.class.getDeclaredField("messageBundle");
        field.setAccessible(true);
        field.set(null, mockBundle);
    }
}