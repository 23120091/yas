package com.yas.sampledata.viewmodel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SampleDataVmTest {

    @Test
    void shouldCreateSampleDataVmWithMessage() {
        // Arrange
        String message = "Insert Sample Data successfully!";

        // Act
        SampleDataVm vm = new SampleDataVm(message);

        // Assert
        assertNotNull(vm);
        assertEquals(message, vm.message());
    }

    @Test
    void shouldAllowNullMessage() {
        // Act
        SampleDataVm vm = new SampleDataVm(null);

        // Assert
        assertNull(vm.message());
    }

    @Test
    void shouldHaveCorrectEquality() {
        // Arrange
        SampleDataVm vm1 = new SampleDataVm("OK");
        SampleDataVm vm2 = new SampleDataVm("OK");

        // Assert
        assertEquals(vm1, vm2);
        assertEquals(vm1.hashCode(), vm2.hashCode());
    }

    @Test
    void shouldHaveDifferentObjectsWhenMessageDifferent() {
        // Arrange
        SampleDataVm vm1 = new SampleDataVm("OK");
        SampleDataVm vm2 = new SampleDataVm("FAIL");

        // Assert
        assertNotEquals(vm1, vm2);
    }
}