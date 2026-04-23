package com.yas.sampledata.utils;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;

class SqlScriptExecutorTest {

    @Test
    void executeScriptsForSchema_shouldNotThrowException() {
        // Arrange
        SqlScriptExecutor executor = new SqlScriptExecutor();
        DataSource dataSource = mock(DataSource.class);

        // Act + Assert
        assertDoesNotThrow(() ->
                executor.executeScriptsForSchema(
                        dataSource,
                        "public",
                        "classpath*:db/test/*.sql"
                )
        );
    }
}