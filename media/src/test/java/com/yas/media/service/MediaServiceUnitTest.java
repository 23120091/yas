package com.yas.media.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.http.MediaType;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.anyLong;

import com.yas.commonlibrary.exception.NotFoundException;
import com.yas.media.config.YasConfig;
import com.yas.media.mapper.MediaVmMapper;
import com.yas.media.model.Media;
import com.yas.media.model.dto.MediaDto;
import com.yas.media.model.dto.MediaDto.MediaDtoBuilder;
import com.yas.media.repository.FileSystemRepository;
import com.yas.media.repository.MediaRepository;
import com.yas.media.service.MediaServiceImpl;
import com.yas.media.viewmodel.MediaPostVm;
import com.yas.media.viewmodel.MediaVm;
import com.yas.media.viewmodel.NoFileMediaVm;
import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class MediaServiceUnitTest {

    @Spy
    private MediaVmMapper mediaVmMapper = Mappers.getMapper(MediaVmMapper.class);

    @Mock
    private MediaRepository mediaRepository;

    @Mock
    private FileSystemRepository fileSystemRepository;

    @Mock
    private YasConfig yasConfig;

    @Mock
    private MediaDtoBuilder builder;

    @InjectMocks
    private MediaServiceImpl mediaService;

    private Media media;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        media = new Media();
        media.setId(1L);
        media.setCaption("test");
        media.setFileName("file");
        media.setMediaType("image/jpeg");
    }

    @Test
    void getMedia_whenValidId_thenReturnData() {
        NoFileMediaVm noFileMediaVm = new NoFileMediaVm(1L, "Test", "fileName", "image/png");
        when(mediaRepository.findByIdWithoutFileInReturn(1L)).thenReturn(noFileMediaVm);
        when(yasConfig.publicUrl()).thenReturn("/media/");

        MediaVm mediaVm = mediaService.getMediaById(1L);
        assertNotNull(mediaVm);
        assertEquals("Test", mediaVm.getCaption());
        assertEquals("fileName", mediaVm.getFileName());
        assertEquals("image/png", mediaVm.getMediaType());
        assertEquals(String.format("/media/medias/%s/file/%s", 1L, "fileName"), mediaVm.getUrl());
    }

    @Test
    void getMedia_whenMediaNotFound_thenReturnNull() {
        when(mediaRepository.findById(1L)).thenReturn(Optional.empty());

        MediaVm mediaVm = mediaService.getMediaById(1L);
        assertNull(mediaVm);
    }

    @Test
    void removeMedia_whenMediaNotFound_thenThrowsNotFoundException() {
        when(mediaRepository.findById(1L)).thenReturn(Optional.empty());

        NotFoundException exception = assertThrows(NotFoundException.class, () -> mediaService.removeMedia(1L));
        assertEquals(String.format("Media %s is not found", 1L), exception.getMessage());
    }

    @Test
    void removeMedia_whenValidId_thenRemoveSuccess() {
        NoFileMediaVm noFileMediaVm = new NoFileMediaVm(1L, "Test", "fileName", "image/png");
        when(mediaRepository.findByIdWithoutFileInReturn(1L)).thenReturn(noFileMediaVm);
        doNothing().when(mediaRepository).deleteById(1L);

        mediaService.removeMedia(1L);

        verify(mediaRepository, times(1)).deleteById(1L);
    }

    @Test
    void removeMedia_whenMediaDoesNotExist_shouldNeverCallDelete() {
        // Given
        when(mediaRepository.findByIdWithoutFileInReturn(99L)).thenReturn(null);

        // When & Then
        assertThrows(NotFoundException.class, () -> mediaService.removeMedia(99L));
        // Verify rằng deleteById chưa bao giờ được thực hiện để bảo vệ dữ liệu
        verify(mediaRepository, never()).deleteById(anyLong());
    }

    @Test
    void saveMedia_whenTypePNG_thenSaveSuccess() {
        byte[] pngFileContent = new byte[] {};
        MultipartFile multipartFile = new MockMultipartFile(
            "file",
            "example.png",
            "image/png",
            pngFileContent
        );
        MediaPostVm mediaPostVm = new MediaPostVm("media", multipartFile, "fileName");

        when(mediaRepository.save(any(Media.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Media mediaSave = mediaService.saveMedia(mediaPostVm);
        assertNotNull(mediaSave);
        assertEquals("media", mediaSave.getCaption());
        assertEquals("fileName", mediaSave.getFileName());
    }

    @Test
    void saveMedia_whenTypeJPEG_thenSaveSuccess() {
        byte[] pngFileContent = new byte[] {};
        MultipartFile multipartFile = new MockMultipartFile(
            "file",
            "example.jpeg",
            "image/jpeg",
            pngFileContent
        );
        MediaPostVm mediaPostVm = new MediaPostVm("media", multipartFile, "fileName");

        when(mediaRepository.save(any(Media.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Media mediaSave = mediaService.saveMedia(mediaPostVm);
        assertNotNull(mediaSave);
        assertEquals("media", mediaSave.getCaption());
        assertEquals("fileName", mediaSave.getFileName());
    }

    @Test
    void saveMedia_whenTypeGIF_thenSaveSuccess() {
        byte[] gifFileContent = new byte[] {};
        MultipartFile multipartFile = new MockMultipartFile(
            "file",
            "example.gif",
            "image/gif",
            gifFileContent
        );
        MediaPostVm mediaPostVm = new MediaPostVm("media", multipartFile, "fileName");

        when(mediaRepository.save(any(Media.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Media mediaSave = mediaService.saveMedia(mediaPostVm);
        assertNotNull(mediaSave);
        assertEquals("media", mediaSave.getCaption());
        assertEquals("fileName", mediaSave.getFileName());
    }

    @Test
    void saveMedia_whenFileNameIsNull_thenOk() {
        byte[] pngFileContent = new byte[] {};
        MultipartFile multipartFile = new MockMultipartFile(
            "file",
            "example.png",
            "image/png",
            pngFileContent
        );
        MediaPostVm mediaPostVm = new MediaPostVm("media", multipartFile, null);

        when(mediaRepository.save(any(Media.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Media mediaSave = mediaService.saveMedia(mediaPostVm);
        assertNotNull(mediaSave);
        assertEquals("media", mediaSave.getCaption());
        assertEquals("example.png", mediaSave.getFileName());
    }

    @Test
    void saveMedia_whenFileNameIsEmpty_thenOk() {
        byte[] pngFileContent = new byte[] {};
        MultipartFile multipartFile = new MockMultipartFile(
            "file",
            "example.png",
            "image/png",
            pngFileContent
        );
        MediaPostVm mediaPostVm = new MediaPostVm("media", multipartFile, "");

        when(mediaRepository.save(any(Media.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Media mediaSave = mediaService.saveMedia(mediaPostVm);
        assertNotNull(mediaSave);
        assertEquals("media", mediaSave.getCaption());
        assertEquals("example.png", mediaSave.getFileName());
    }

    @Test
    void saveMedia_whenFileNameIsBlank_thenOk() {
        byte[] pngFileContent = new byte[] {};
        MultipartFile multipartFile = new MockMultipartFile(
            "file",
            "example.png",
            "image/png",
            pngFileContent
        );
        MediaPostVm mediaPostVm = new MediaPostVm("media", multipartFile, "   ");

        when(mediaRepository.save(any(Media.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Media mediaSave = mediaService.saveMedia(mediaPostVm);
        assertNotNull(mediaSave);
        assertEquals("media", mediaSave.getCaption());
        assertEquals("example.png", mediaSave.getFileName());
    }

    @Test
    void saveMedia_whenOriginalFilenameHasNoExtension_shouldStillSave() {
        // Given: File tên "README" không có đuôi .txt hay .png
        MockMultipartFile file = new MockMultipartFile("file", "README", "text/plain", "data".getBytes());
        MediaPostVm vm = new MediaPostVm("no extension", file, null);

        when(mediaRepository.save(any(Media.class))).thenAnswer(i -> i.getArgument(0));

        // When
        Media result = mediaService.saveMedia(vm);

        // Then
        assertEquals("README", result.getFileName());
        assertNotNull(result.getMediaType());
    }

    @Test
    void saveMedia_whenFileIsEmpty_shouldStillProcess() throws IOException {
        // Given: File rỗng
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);
        MediaPostVm vm = new MediaPostVm("empty file", emptyFile, null);

        when(mediaRepository.save(any(Media.class))).thenAnswer(i -> i.getArgument(0));

        // When
        Media result = mediaService.saveMedia(vm);

        // Then
        assertNotNull(result);
        assertEquals(0, emptyFile.getSize());
        verify(fileSystemRepository).persistFile(eq("empty.txt"), any(byte[].class));
    }

    @Test
    void saveMedia_whenFileNameOverrideHasSpaces_shouldTrimCorrectly() {
        // Given: Tên override có khoảng trắng thừa
        MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png", "data".getBytes());
        MediaPostVm vm = new MediaPostVm("caption", file, "  my-clean-name.png  ");

        when(mediaRepository.save(any(Media.class))).thenAnswer(i -> i.getArgument(0));

        // When
        Media result = mediaService.saveMedia(vm);

        // Then: Tên file lưu vào DB phải được cắt bỏ khoảng trắng
        assertEquals("my-clean-name.png", result.getFileName());
    }

    @Test
    void getFile_whenMediaNotFound_thenReturnMediaDto() {
        MediaDto expectedDto = MediaDto.builder().build();
        when(mediaRepository.findById(1L)).thenReturn(Optional.ofNullable(null));
        when(builder.build()).thenReturn(expectedDto);

        MediaDto mediaDto = mediaService.getFile(1L, "fileName");

        assertEquals(expectedDto.getMediaType(), mediaDto.getMediaType());
        assertEquals(expectedDto.getContent(), mediaDto.getContent());
    }

    @Test
    void getFile_whenMediaNameNotMatch_thenReturnMediaDto() {
        MediaDto expectedDto = MediaDto.builder().build();
        when(mediaRepository.findById(1L)).thenReturn(Optional.ofNullable(media));
        when(builder.build()).thenReturn(expectedDto);

        MediaDto mediaDto = mediaService.getFile(1L, "fileName");

        assertEquals(expectedDto.getMediaType(), mediaDto.getMediaType());
        assertEquals(expectedDto.getContent(), mediaDto.getContent());
    }

    @Test
    void getFile_whenFileSystemThrowsException_shouldPropagateException() {
        // Given
        media.setFileName("test.png");
        media.setFilePath("path/to/test.png");
        media.setMediaType("image/png");
        when(mediaRepository.findById(1L)).thenReturn(Optional.of(media));
        
        // Giả lập FileSystemRepository ném RuntimeException
        when(fileSystemRepository.getFile(anyString())).thenThrow(new RuntimeException("Storage access denied"));

        // When & Then
        assertThrows(RuntimeException.class, () -> mediaService.getFile(1L, "test.png"));
    }

    @Test
    void getFileByIds() {
        // Given
        var ip15 = getMedia(-1L, "Iphone 15");
        var macbook = getMedia(-2L, "Macbook");
        var existingMedias = List.of(ip15, macbook);
        when(mediaRepository.findAllById(List.of(ip15.getId(), macbook.getId())))
            .thenReturn(existingMedias);
        when(yasConfig.publicUrl()).thenReturn("https://media/");

        // When
        var medias = mediaService.getMediaByIds(List.of(ip15.getId(), macbook.getId()));

        // Then
        assertFalse(medias.isEmpty());
        verify(mediaVmMapper, times(existingMedias.size())).toVm(any());
        assertThat(medias).allMatch(m -> m.getUrl() != null);
    }

    private static @NotNull Media getMedia(Long id, String name) {
        var media = new Media();
        media.setId(id);
        media.setFileName(name);
        return media;
    }

    @Test
    void getFile_WhenValidIdAndName_ShouldReturnMediaDto() throws IOException {
        // Arrange
        Long mediaId = 1L;
        String fileName = "test.png";
        media.setFileName(fileName);
        media.setMediaType("image/png");
        media.setFilePath("path/to/test.png");

        byte[] content = "fake-image-data".getBytes();
        java.io.InputStream inputStream = new java.io.ByteArrayInputStream(content);

        when(mediaRepository.findById(mediaId)).thenReturn(Optional.of(media));
        when(fileSystemRepository.getFile(media.getFilePath())).thenReturn(inputStream);

        // Act
        MediaDto result = mediaService.getFile(mediaId, fileName);

        // Assert
        assertNotNull(result.getContent());
        assertEquals(org.springframework.http.MediaType.IMAGE_PNG, result.getMediaType());
    }

    @Test
    void getFile_whenFileNameMatchesCaseInsensitive_shouldStillReturnData() throws IOException {
        // Given: Tên trong DB là viết hoa hoàn toàn
        media.setFileName("PHOTO.PNG");
        media.setMediaType("image/png");
        media.setFilePath("path/photo.png");
        when(mediaRepository.findById(1L)).thenReturn(Optional.of(media));
        when(fileSystemRepository.getFile(anyString())).thenReturn(new ByteArrayInputStream("data".getBytes()));

        // When: Yêu cầu file bằng chữ thường
        MediaDto result = mediaService.getFile(1L, "photo.png");

        // Then: Vẫn phải trả về dữ liệu thành công
        assertNotNull(result.getContent());
        assertEquals(MediaType.IMAGE_PNG, result.getMediaType());
    }

    @Test
    void getMediaByIds_whenEmptyList_thenReturnEmptyList() {
        // Given
        List<Long> ids = List.of();
        when(mediaRepository.findAllById(ids)).thenReturn(List.of());

        // When
        List<MediaVm> result = mediaService.getMediaByIds(ids);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void saveMedia_withFileNameOverride_shouldUseOverrideName() throws IOException {
        // Given
        String overrideName = "new-name.png";
        MockMultipartFile file = new MockMultipartFile("file", "original.png", "image/png", "data".getBytes());
        MediaPostVm vm = new MediaPostVm("caption", file, overrideName);

        when(fileSystemRepository.persistFile(anyString(), any())).thenReturn("stored/path");
        when(mediaRepository.save(any(Media.class))).thenAnswer(i -> i.getArgument(0));

        // When
        Media result = mediaService.saveMedia(vm);

        // Then
        assertEquals(overrideName, result.getFileName());
        verify(fileSystemRepository).persistFile(eq(overrideName), any());
    }

    @Test
    void getFile_whenIdFoundButNameNotMatch_thenReturnEmptyContent() {
        // Trường hợp tìm thấy ID nhưng tên file cung cấp khác với tên file trong DB
        media.setFileName("real-file.png");
        when(mediaRepository.findById(1L)).thenReturn(Optional.of(media));

        MediaDto result = mediaService.getFile(1L, "wrong-file-name.png");

        assertNull(result.getContent());
        assertNull(result.getMediaType());
    }

    @Test
    void getMediaByIds_whenSomeIdsExistAndSomeNot_thenReturnOnlyFound() {
        // Case: Truy vấn danh sách ID nhưng chỉ một số ID tồn tại trong DB
        Media m1 = getMedia(1L, "found.png");
        when(mediaRepository.findAllById(any())).thenReturn(List.of(m1));
        when(yasConfig.publicUrl()).thenReturn("http://localhost");

        List<MediaVm> result = mediaService.getMediaByIds(List.of(1L, 99L));

        assertEquals(1, result.size());
        assertEquals("found.png", result.get(0).getFileName());
    }

    @Test
    void getMediaByIds_whenListHasPartialMatches_shouldReturnOnlyFoundOnes() {
        // Given: Chỉ có media 1 tồn tại
        Media m1 = new Media();
        m1.setId(1L);
        m1.setFileName("file1.png");
        
        when(mediaRepository.findAllById(any())).thenReturn(List.of(m1));
        when(yasConfig.publicUrl()).thenReturn("http://localhost");

        // When
        List<MediaVm> results = mediaService.getMediaByIds(List.of(1L, 99L));

        // Then: Chỉ trả về 1 phần tử
        assertEquals(1, results.size());
        assertEquals("file1.png", results.get(0).getFileName());
    }

    @Test
    void saveMedia_whenOverrideNameIsBlank_shouldUseOriginalFileName() {
        // Given: Tên override để trống
        MockMultipartFile file = new MockMultipartFile("file", "original_name.jpg", "image/jpeg", "data".getBytes());
        MediaPostVm vm = new MediaPostVm("caption", file, ""); // Tên trống

        when(mediaRepository.save(any(Media.class))).thenAnswer(i -> i.getArgument(0));

        // When
        Media result = mediaService.saveMedia(vm);

        // Then: Code sẽ lấy tên gốc "original_name.jpg"
        assertEquals("original_name.jpg", result.getFileName());
    }

    @Test
    void saveMedia_whenIOException_thenThrowsException() throws IOException {
        // Mock một file bị lỗi khi truy cập nội dung
        MultipartFile multipartFile = mock(MultipartFile.class);
        when(multipartFile.getContentType()).thenReturn("image/png");
        when(multipartFile.getOriginalFilename()).thenReturn("error.png");
        when(multipartFile.getBytes()).thenThrow(new IOException("Read error"));

        MediaPostVm mediaPostVm = new MediaPostVm("test", multipartFile, null);

        assertThrows(IOException.class, () -> mediaService.saveMedia(mediaPostVm));
    }

    @Test
    void getFile_whenInvalidMediaTypeInDb_thenThrowsException() {
        // Given: Media có type không đúng chuẩn (ví dụ: "not-a-mime-type")
        media.setFileName("test.png");
        media.setMediaType("invalid-type"); 
        when(mediaRepository.findById(1L)).thenReturn(Optional.of(media));

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> mediaService.getFile(1L, "test.png"));
    }

    @Test
    void getMediaByIds_whenMapperReturnsNull_thenFilterShouldHandle() {
        // Given
        Media m1 = getMedia(1L, "file1.png");
        when(mediaRepository.findAllById(any())).thenReturn(List.of(m1));
        // Giả sử mapper trả về null
        when(mediaVmMapper.toVm(any())).thenReturn(null);

        // When & Then
        assertThrows(NullPointerException.class, () -> mediaService.getMediaByIds(List.of(1L)));
    }

    @Test
    void getMediaById_withSpecialCharsInFileName_shouldReturnCorrectPath() {
        // Given
        NoFileMediaVm noFileMediaVm = new NoFileMediaVm(1L, "Test", "file name với space.png", "image/png");
        when(mediaRepository.findByIdWithoutFileInReturn(1L)).thenReturn(noFileMediaVm);
        when(yasConfig.publicUrl()).thenReturn("http://localhost:8080");

        // When
        MediaVm result = mediaService.getMediaById(1L);

        // Then
        // Kiểm tra URL chứa đúng tên file thô nếu code không gọi .encode()
        assertThat(result.getUrl()).contains("file/file name với space.png");
    }

    @Test
    void getMediaById_whenRepositoryReturnsNull_thenReturnNull() {
        // Given
        when(mediaRepository.findByIdWithoutFileInReturn(1L)).thenReturn(null);

        // When
        MediaVm result = mediaService.getMediaById(1L);

        // Then
        assertNull(result);
    }

    @Test
    void getMediaById_shouldMapAllFieldsCorrectly() {
        // Given
        NoFileMediaVm dto = new NoFileMediaVm(10L, "Super Caption", "image.jpg", "image/jpeg");
        when(mediaRepository.findByIdWithoutFileInReturn(10L)).thenReturn(dto);
        when(yasConfig.publicUrl()).thenReturn("http://api.yas.com");

        // When
        MediaVm result = mediaService.getMediaById(10L);

        // Then
        assertAll("Verify all fields mapped",
            () -> assertEquals(10L, result.getId()),
            () -> assertEquals("Super Caption", result.getCaption()),
            () -> assertEquals("image.jpg", result.getFileName()),
            () -> assertEquals("image/jpeg", result.getMediaType()),
            () -> assertTrue(result.getUrl().contains("/medias/10/file/image.jpg"))
        );
    }
}
