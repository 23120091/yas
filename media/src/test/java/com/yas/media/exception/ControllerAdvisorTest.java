package com.yas.media.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.yas.commonlibrary.exception.NotFoundException;
import com.yas.media.viewmodel.ErrorVm;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.ServletWebRequest;

class ControllerAdvisorTest {

    @InjectMocks
    private ControllerAdvisor controllerAdvisor;

    @BeforeEach
    void setUp() {
        // Khởi tạo và tiêm Mock vào controllerAdvisor
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void handleNotFoundException_ShouldReturn404() {
        // Mock ServletWebRequest để tránh lỗi ClassCastException
        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        when(mockRequest.getServletPath()).thenReturn("/api/medias/1");
        
        ServletWebRequest servletWebRequest = mock(ServletWebRequest.class);
        when(servletWebRequest.getRequest()).thenReturn(mockRequest);

        NotFoundException ex = new NotFoundException("Media 1 is not found");

        ResponseEntity<ErrorVm> response = controllerAdvisor.handleNotFoundException(ex, servletWebRequest);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Media 1 is not found", response.getBody().detail());
        assertEquals("Not Found", response.getBody().title());
    }

    @Test
    void handleMethodArgumentNotValid_ShouldReturn400_WithValidationErrors() {
        // Mock MethodArgumentNotValidException (Lỗi khi @Valid DTO fail)
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        
        FieldError fieldError = new FieldError("mediaPostVm", "file", "File is mandatory");
        
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

        ResponseEntity<ErrorVm> response = controllerAdvisor.handleMethodArgumentNotValid(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertThat(response.getBody().fieldErrors()).contains("file File is mandatory");
    }

    @Test
    void handleOtherException_ShouldReturn500() {
        // Xử lý các lỗi Runtime hoặc lỗi hệ thống chưa xác định
        Exception ex = new Exception("Internal Server Error occurred");
        ServletWebRequest request = mock(ServletWebRequest.class);
        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        
        when(request.getRequest()).thenReturn(mockRequest);
        when(mockRequest.getServletPath()).thenReturn("/api/error-test");

        ResponseEntity<ErrorVm> response = controllerAdvisor.handleOtherException(ex, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("Internal Server Error occurred", response.getBody().detail());
    }

    @Test
    void testErrorVm_FullCoverage() {
        // Test này giúp phủ 100% cho Record/Model ErrorVm (Getter/Setter)
        List<String> fieldErrors = List.of("field1 error");
        ErrorVm errorVm = new ErrorVm("400", "Bad Request", "Detail info", fieldErrors);
        
        assertEquals("400", errorVm.statusCode());
        assertEquals("Bad Request", errorVm.title());
        assertEquals("Detail info", errorVm.detail());
        assertEquals(fieldErrors, errorVm.fieldErrors());
    }
}