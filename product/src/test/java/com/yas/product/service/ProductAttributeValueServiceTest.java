package com.yas.product.service;

import com.yas.commonlibrary.exception.BadRequestException;
import com.yas.commonlibrary.exception.DuplicatedException;
import com.yas.commonlibrary.exception.NotFoundException;
import com.yas.product.model.attribute.ProductAttribute;
import com.yas.product.model.attribute.ProductAttributeGroup;
import com.yas.product.repository.ProductAttributeGroupRepository;
import com.yas.product.repository.ProductAttributeRepository;
import com.yas.product.viewmodel.productattribute.ProductAttributePostVm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductAttributeValueServiceTest {

    @Mock
    private ProductAttributeRepository productAttributeRepository;

    @Mock
    private ProductAttributeGroupRepository productAttributeGroupRepository;

    @InjectMocks
    private ProductAttributeService productAttributeService;

    private ProductAttributePostVm postVm;

    @BeforeEach
    void setUp() {
        // Chuẩn bị dữ liệu mẫu cho ViewModel
        postVm = new ProductAttributePostVm("Color", 1L);
    }

    @Test
    void save_WhenNameAlreadyExisted_ShouldThrowDuplicatedException() {
        // Giả lập tên đã tồn tại
        when(productAttributeRepository.findExistedName("Color", null)).thenReturn(new ProductAttribute());

        assertThrows(DuplicatedException.class, () -> productAttributeService.save(postVm));
        verify(productAttributeRepository, never()).save(any());
    }

    @Test
    void save_WhenGroupNotFound_ShouldThrowBadRequestException() {
        // Giả lập tên chưa tồn tại nhưng không tìm thấy Group
        when(productAttributeRepository.findExistedName("Color", null)).thenReturn(null);
        when(productAttributeGroupRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(BadRequestException.class, () -> productAttributeService.save(postVm));
    }

    @Test
    void update_WhenAttributeNotFound_ShouldThrowNotFoundException() {
        Long attributeId = 1L;
        when(productAttributeRepository.findExistedName("Color", attributeId)).thenReturn(null);
        when(productAttributeRepository.findById(attributeId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> productAttributeService.update(postVm, attributeId));
    }

    @Test
    void update_Success_ShouldReturnUpdatedAttribute() {
        Long attributeId = 1L;
        ProductAttribute existingAttribute = new ProductAttribute();
        existingAttribute.setId(attributeId);
        
        ProductAttributeGroup group = new ProductAttributeGroup();
        group.setId(1L);

        when(productAttributeRepository.findExistedName("Color", attributeId)).thenReturn(null);
        when(productAttributeRepository.findById(attributeId)).thenReturn(Optional.of(existingAttribute));
        when(productAttributeGroupRepository.findById(1L)).thenReturn(Optional.of(group));
        when(productAttributeRepository.save(any())).thenReturn(existingAttribute);

        ProductAttribute result = productAttributeService.update(postVm, attributeId);

        assertNotNull(result);
        verify(productAttributeRepository).save(any());
    }
}