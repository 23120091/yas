package com.yas.sampledata.controller;

import com.yas.sampledata.service.SampleDataService;
import com.yas.sampledata.viewmodel.SampleDataVm;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class SampleDataControllerTest {

    @Test
    void createSampleData_shouldReturnResultFromService() {
        // Arrange
        SampleDataService service = mock(SampleDataService.class);

        SampleDataVm mockResponse = new SampleDataVm("OK");
        when(service.createSampleData()).thenReturn(mockResponse);

        SampleDataController controller = new SampleDataController(service);

        // Act
        SampleDataVm result = controller.createSampleData(null);

        // Assert
        assertEquals("OK", result.message());
    }
}