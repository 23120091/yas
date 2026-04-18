package com.yas.media.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yas.commonlibrary.exception.NotFoundException;
import com.yas.media.viewmodel.ErrorVm;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.ServletWebRequest;

class ControllerAdvisorTest {

    @InjectMocks
    private ControllerAdvisor controllerAdvisor;

    @BeforeEach
    void setUp() {
        // Khởi tạo các Mock và Inject chúng vào controllerAdvisor
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void handleNotFoundException_ShouldReturn404() {
        // Mock các đối tượng cần thiết để tránh lỗi ClassCast
        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        when(mockRequest.getServletPath()).thenReturn("/api/test");
        
        ServletWebRequest servletWebRequest = mock(ServletWebRequest.class);
        when(servletWebRequest.getRequest()).thenReturn(mockRequest);

        NotFoundException ex = new NotFoundException("Media 1 is not found");

        // Bây giờ biến controllerAdvisor đã tồn tại và có thể sử dụng
        ResponseEntity<ErrorVm> response = controllerAdvisor.handleNotFoundException(ex, servletWebRequest);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Media 1 is not found", response.getBody().detail());
    }
}