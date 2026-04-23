package com.yas.product.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yas.product.model.enumeration.DimensionUnit;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductTest {

    @Test
    void testNoArgsConstructor() {
        Product product = new Product();
        assertNotNull(product.getRelatedProducts());
        assertNotNull(product.getProductCategories());
        assertNotNull(product.getProductImages());
        assertNotNull(product.getAttributeValues());
    }

    @Test
    void testBuilderAndGettersSetters() {
        Brand brand = new Brand();
        brand.setName("Sony");

        Product parent = Product.builder().id(2L).name("Parent Product").build();

        Product product = Product.builder()
                .id(1L)
                .name("PlayStation 5")
                .shortDescription("Gaming Console")
                .description("Detailed description")
                .specification("Specs")
                .sku("PS5-001")
                .gtin("123456789")
                .slug("playstation-5")
                .price(499.99)
                .hasOptions(true)
                .isAllowedToOrder(true)
                .isPublished(true)
                .isFeatured(true)
                .isVisibleIndividually(true)
                .stockTrackingEnabled(true)
                .stockQuantity(100L)
                .taxClassId(1L)
                .metaTitle("PS5")
                .metaKeyword("gaming, console")
                .metaDescription("Buy PS5")
                .thumbnailMediaId(10L)
                .weight(4.5)
                .dimensionUnit(DimensionUnit.CM)
                .length(39.0)
                .width(10.4)
                .height(26.0)
                .brand(brand)
                .parent(parent)
                .taxIncluded(true)
                .build();

        // Verify basic fields
        assertEquals(1L, product.getId());
        assertEquals("PlayStation 5", product.getName());
        assertEquals(499.99, product.getPrice());
        assertEquals("Sony", product.getBrand().getName());
        assertEquals(DimensionUnit.CM, product.getDimensionUnit());
        assertEquals(2L, product.getParent().getId());
        assertTrue(product.isFeatured());
        assertTrue(product.isTaxIncluded());
        
        // Test Setters
        product.setStockQuantity(50L);
        assertEquals(50L, product.getStockQuantity());
    }

    @Test
    void testEqualsAndHashCode() {
        Product product1 = new Product();
        product1.setId(1L);

        Product product2 = new Product();
        product2.setId(1L);

        Product product3 = new Product();
        product3.setId(2L);

        // Test identity
        assertEquals(product1, product1);
        
        // Test equality based on ID
        assertEquals(product1, product2);
        assertEquals(product1.hashCode(), product2.hashCode());

        // Test inequality
        assertNotEquals(product1, product3);
        assertNotEquals(product1, new Object());
        assertNotEquals(product1, null);
    }

    @Test
    void testCollectionsInitialization() {
        // Kiểm tra xem Builder.Default có hoạt động không (không bị Null)
        Product product = Product.builder().build();
        
        assertNotNull(product.getRelatedProducts());
        assertNotNull(product.getProductCategories());
        assertNotNull(product.getProductImages());
        assertNotNull(product.getAttributeValues());
        assertNotNull(product.getProducts());
        
        // Test add to collection
        product.getProductImages().add(new ProductImage());
        assertEquals(1, product.getProductImages().size());
    }
}