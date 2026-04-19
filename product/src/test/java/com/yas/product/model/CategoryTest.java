package com.yas.product.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CategoryTest {

    @Test
    void testCategoryProperties() {
        // Setup parent category
        Category parentCategory = new Category();
        parentCategory.setId(1L);
        parentCategory.setName("Electronics");

        // Setup main category
        Category category = new Category();
        category.setId(2L);
        category.setName("Laptops");
        category.setDescription("Mobile computers");
        category.setSlug("laptops");
        category.setMetaKeyword("laptop, computer");
        category.setMetaDescription("Best laptops");
        category.setDisplayOrder((short) 1);
        category.setIsPublished(true);
        category.setImageId(100L);
        category.setParent(parentCategory);

        // Verify basic fields
        assertEquals(2L, category.getId());
        assertEquals("Laptops", category.getName());
        assertEquals("Mobile computers", category.getDescription());
        assertEquals("laptops", category.getSlug());
        assertEquals("laptop, computer", category.getMetaKeyword());
        assertEquals("Best laptops", category.getMetaDescription());
        assertEquals((short) 1, category.getDisplayOrder());
        assertTrue(category.getIsPublished());
        assertEquals(100L, category.getImageId());
        
        // Verify relationship
        assertNotNull(category.getParent());
        assertEquals(1L, category.getParent().getId());
    }

    @Test
    void testCollectionsInitialization() {
        Category category = new Category();
        
        // Kiểm tra khởi tạo ArrayList mặc định
        assertNotNull(category.getCategories());
        assertNotNull(category.getProductCategories());
        
        // Test thêm phần tử vào list
        Category subCategory = new Category();
        category.getCategories().add(subCategory);
        assertEquals(1, category.getCategories().size());

        ProductCategory productCategory = new ProductCategory();
        category.getProductCategories().add(productCategory);
        assertEquals(1, category.getProductCategories().size());
    }

    @Test
    void testEqualsAndHashCode() {
        Category category1 = new Category();
        category1.setId(1L);

        Category category2 = new Category();
        category2.setId(1L);

        Category category3 = new Category();
        category3.setId(2L);

        // Test identity
        assertEquals(category1, category1);
        
        // Test equality based on ID
        assertEquals(category1, category2);
        assertEquals(category1.hashCode(), category2.hashCode());

        // Test inequality
        assertNotEquals(category1, category3);
        assertNotEquals(category1, null);
        assertNotEquals(category1, new Object());
    }

    @Test
    void testAllArgsConstructor() {
        Category parent = new Category();
        List<Category> children = new ArrayList<>();
        List<ProductCategory> productCategories = new ArrayList<>();
        
        Category category = new Category(
            10L, 
            "Home", 
            "Home desc", 
            "home", 
            "key", 
            "desc", 
            (short) 0, 
            true, 
            50L, 
            parent, 
            children, 
            productCategories
        );

        assertNotNull(category);
        assertEquals(10L, category.getId());
        assertEquals("Home", category.getName());
    }
}