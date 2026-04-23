package com.yas.media.viewmodel;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.util.List;

class ViewModelTest {

    @Test
    void testErrorVm_FullConstructor() {
        List<String> fieldErrors = List.of("error1", "error2");
        ErrorVm errorVm = new ErrorVm("400", "Bad Request", "Detail info", fieldErrors);
        
        assertEquals("400", errorVm.statusCode());
        assertEquals("Bad Request", errorVm.title());
        assertEquals("Detail info", errorVm.detail());
        assertEquals(fieldErrors, errorVm.fieldErrors());
    }

    @Test
    void testErrorVm_PartialConstructor() {
        // Test constructor thứ 2 (chỉ có 3 tham số) để phủ logic khởi tạo ArrayList mới
        ErrorVm errorVm = new ErrorVm("500", "Server Error", "Something went wrong");
        
        assertEquals("500", errorVm.statusCode());
        assertNotNull(errorVm.fieldErrors());
        assertTrue(errorVm.fieldErrors().isEmpty());
    }

    @Test
    void testMediaPostVm() {
        MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png", "data".getBytes());
        MediaPostVm vm = new MediaPostVm("Test Caption", file, "new-name.png");
        
        assertEquals("Test Caption", vm.caption());
        assertEquals(file, vm.multipartFile());
        assertEquals("new-name.png", vm.fileNameOverride());
    }

    @Test
    void testMediaVm_GettersAndSetters() {
        // Phủ class có @Getter @Setter của Lombok
        MediaVm vm = new MediaVm(1L, "Caption", "file.jpg", "image/jpeg", "http://url");
        
        // Kiểm tra Getter
        assertEquals(1L, vm.getId());
        assertEquals("Caption", vm.getCaption());
        assertEquals("file.jpg", vm.getFileName());
        assertEquals("image/jpeg", vm.getMediaType());
        assertEquals("http://url", vm.getUrl());

        // Kiểm tra Setter (ép Jacoco quét qua code do Lombok sinh ra)
        vm.setId(2L);
        vm.setCaption("New Caption");
        assertEquals(2L, vm.getId());
        assertEquals("New Caption", vm.getCaption());
    }

    @Test
    void testNoFileMediaVm() {
        NoFileMediaVm noFile = new NoFileMediaVm(1L, "Caption", "file.png", "image/png");
        
        assertEquals(1L, noFile.id());
        assertEquals("Caption", noFile.caption());
        assertEquals("file.png", noFile.fileName());
        assertEquals("image/png", noFile.mediaType());
    }
}