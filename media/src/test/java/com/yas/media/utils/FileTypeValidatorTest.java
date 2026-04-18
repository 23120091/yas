package com.yas.media.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.io.IOException;

class FileTypeValidatorTest {

    private FileTypeValidator validator;
    private ConstraintValidatorContext context;

    @BeforeEach
    void setUp() {
        validator = new FileTypeValidator();
        context = mock(ConstraintValidatorContext.class);
        
        // Thay vì dùng when(annotation.message()), chúng ta tạo một Mock trực tiếp
        ValidFileType annotation = mock(ValidFileType.class);
        
        // Đảm bảo tên phương thức khớp chính xác với file ValidFileType.java của bạn
        when(annotation.allowedTypes()).thenReturn(new String[]{"image/png", "image/jpeg"});
        when(annotation.message()).thenReturn("Invalid file type");
        
        // Mock fluent API của context để tránh NullPointerException
        ConstraintValidatorContext.ConstraintViolationBuilder builder = mock(ConstraintValidatorContext.ConstraintViolationBuilder.class);
        when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(builder);
        
        validator.initialize(annotation);
    }

    @Test
    void isValid_whenFileIsNull_returnFalse() {
        assertFalse(validator.isValid(null, context));
    }

    @Test
    void isValid_whenNotAllowedType_returnFalse() {
        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", "data".getBytes());
        assertFalse(validator.isValid(file, context));
    }

    @Test
    void isValid_whenIsImageButCorrupted_returnFalse() {
        // Gửi data không phải cấu trúc ảnh thực tế để ImageIO.read trả về null
        MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png", "not-an-image".getBytes());
        assertFalse(validator.isValid(file, context));
    }

    @Test
    void isValid_whenValidImage_returnTrue() throws IOException {
        // Đây là byte array tối thiểu của một file PNG trắng (1x1 pixel) để ImageIO đọc được
        byte[] validPng = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, 0x08, 0x02, 0x00, 0x00, 0x00, (byte) 0x90, 0x77, 0x53, (byte) 0xDE, 0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41, 0x54, 0x08, (byte) 0xD7, 0x63, (byte) 0xF8, (byte) 0xFF, (byte) 0xFF, 0x3F, 0x00, 0x05, (byte) 0xFE, 0x02, (byte) 0xFE, (byte) 0xDC, 0x44, 0x74, (byte) 0x8E, 0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, (byte) 0xAE, 0x42, 0x60, (byte) 0x82};
        
        MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png", validPng);
        assertTrue(validator.isValid(file, context));
    }
}