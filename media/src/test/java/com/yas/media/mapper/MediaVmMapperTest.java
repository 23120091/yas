package com.yas.media.mapper;

import com.yas.media.model.Media;
import com.yas.media.viewmodel.MediaVm;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import static org.junit.jupiter.api.Assertions.*;

class MediaVmMapperTest {

    private final MediaVmMapper mapper = Mappers.getMapper(MediaVmMapper.class);

    @Test
    void toVm_ShouldMapCorrectFields() {
        // Given
        Media media = new Media();
        media.setId(10L);
        media.setCaption("Media Caption");
        media.setFileName("test.jpg");
        media.setMediaType("image/jpeg");

        // When
        MediaVm vm = mapper.toVm(media);

        // Then
        assertNotNull(vm);
        assertEquals(media.getId(), vm.getId());
        assertEquals(media.getCaption(), vm.getCaption());
        assertEquals(media.getFileName(), vm.getFileName());
        assertEquals(media.getMediaType(), vm.getMediaType());
    }

    @Test
    void toVm_NullSource_ShouldReturnNull() {
        assertNull(mapper.toVm(null));
    }
}