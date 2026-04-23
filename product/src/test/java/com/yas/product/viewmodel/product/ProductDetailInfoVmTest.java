package com.yas.product.viewmodel.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yas.product.model.Category;
import com.yas.product.viewmodel.ImageVm;
import com.yas.product.viewmodel.productattribute.ProductAttributeValueGetVm;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductDetailInfoVmTest {

    @Test
    void testConstructorAndGetters() {
        // Prepare data
        List<Category> categories = List.of(new Category());
        List<ProductAttributeValueGetVm> attributes = new ArrayList<>();
        List<ProductVariationGetVm> variations = new ArrayList<>();
        ImageVm thumbnail = new ImageVm(1L, "url");
        List<ImageVm> images = List.of(thumbnail);

        // Execute Constructor
        ProductDetailInfoVm vm = new ProductDetailInfoVm(
                1L, "Product Name", "Short Desc", "Long Desc",
                "Spec", "SKU", "GTIN", "slug",
                true, true, true, true, true, 500.0,
                10L, categories, "Title", "Key", "Meta Desc", 5L,
                "Brand Name", attributes, variations, thumbnail, images
        );

        // Verify
        assertEquals(1L, vm.getId());
        assertEquals("Product Name", vm.getName());
        assertEquals("Short Desc", vm.getShortDescription());
        assertEquals(500.0, vm.getPrice());
        assertEquals(10L, vm.getBrandId());
        assertEquals("Brand Name", vm.getBrandName());
        assertEquals(1, vm.getCategories().size());
        assertEquals(thumbnail, vm.getThumbnail());
        assertEquals(images, vm.getProductImages());
        assertTrue(vm.getIsAllowedToOrder());
        assertTrue(vm.getIsPublished());
    }

    @Test
    void testConstructorWithNullCollections() {
        // Test logic xử lý null trong Constructor của bạn
        ProductDetailInfoVm vm = new ProductDetailInfoVm(
                1L, null, null, null, null, null, null, null,
                null, null, null, null, null, null,
                null, null, null, null, null, null,
                null, null, null, null, null
        );

        // Verify collections are initialized as empty lists instead of null
        assertNotNull(vm.getCategories());
        assertTrue(vm.getCategories().isEmpty());
        
        assertNotNull(vm.getAttributeValues());
        assertTrue(vm.getAttributeValues().isEmpty());
        
        assertNotNull(vm.getVariations());
        assertTrue(vm.getVariations().isEmpty());
    }

    @Test
    void testSetters() {
        ProductDetailInfoVm vm = new ProductDetailInfoVm(
                0, null, null, null, null, null, null, null,
                null, null, null, null, null, null,
                null, null, null, null, null, null,
                null, null, null, null, null
        );

        vm.setName("New Name");
        vm.setPrice(1000.0);
        vm.setSlug("new-slug");

        assertEquals("New Name", vm.getName());
        assertEquals(1000.0, vm.getPrice());
        assertEquals("new-slug", vm.getSlug());
    }
}