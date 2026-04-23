package com.yas.sampledata.service;

import com.yas.sampledata.viewmodel.SampleDataVm;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SampleDataServiceTest {

    @Test
    void createSampleData_shouldReturnSuccessMessage() {
        // Arrange: mock DataSource (không dùng DB thật)
        DataSource productDataSource = mock(DataSource.class);
        DataSource mediaDataSource = mock(DataSource.class);

        // Tạo service với mock
        SampleDataService service = new SampleDataService(productDataSource, mediaDataSource);

        // Act
        SampleDataVm result = service.createSampleData();

        // Assert
        assertNotNull(result, "Result should not be null");

        // ⚠️ tùy vào SampleDataVm là record hay class
        // nếu là record:
        assertEquals("Insert Sample Data successfully!", result.message());

        // nếu là class dùng getter thì thay bằng:
        // assertEquals("Insert Sample Data successfully!", result.getMessage());
    }

    @Test
    void createSampleData_shouldNotThrowException_whenDataSourceIsMocked() {
        // Arrange
        DataSource productDataSource = mock(DataSource.class);
        DataSource mediaDataSource = mock(DataSource.class);

        SampleDataService service = new SampleDataService(productDataSource, mediaDataSource);

        // Act + Assert
        assertDoesNotThrow(service::createSampleData,
                "Service should not throw exception even with mocked DataSource");
    }
}