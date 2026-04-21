package com.yas.sampledata.viewmodel;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ErrorVmTest {

    @Test
    void shouldCreateErrorVmWithAllFields() {
        // Arrange
        List<String> errors = List.of("field1 error", "field2 error");

        // Act
        ErrorVm vm = new ErrorVm("400", "Bad Request", "Invalid input", errors);

        // Assert
        assertEquals("400", vm.statusCode());
        assertEquals("Bad Request", vm.title());
        assertEquals("Invalid input", vm.detail());
        assertEquals(2, vm.fieldErrors().size());
        assertTrue(vm.fieldErrors().contains("field1 error"));
    }

    @Test
    void shouldCreateErrorVmWithDefaultFieldErrors() {
        // Act
        ErrorVm vm = new ErrorVm("404", "Not Found", "Resource not found");

        // Assert
        assertEquals("404", vm.statusCode());
        assertEquals("Not Found", vm.title());
        assertEquals("Resource not found", vm.detail());

        // Quan trọng: constructor này tạo ArrayList rỗng
        assertNotNull(vm.fieldErrors());
        assertTrue(vm.fieldErrors().isEmpty());
    }
}