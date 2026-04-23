package com.yas.product.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProductOptionValueTest {

    @Test
    void testGettersAndSetters() {
        Product product = new Product();
        ProductOption option = new ProductOption();
        
        ProductOptionValue pov = new ProductOptionValue();
        pov.setId(1L);
        pov.setProduct(product);
        pov.setProductOption(option);
        pov.setDisplayType("color");
        pov.setDisplayOrder(1);
        pov.setValue("Red");

        assertEquals(1L, pov.getId());
        assertEquals(product, pov.getProduct());
        assertEquals(option, pov.getProductOption());
        assertEquals("color", pov.getDisplayType());
        assertEquals(1, pov.getDisplayOrder());
        assertEquals("Red", pov.getValue());
    }

    @Test
    void testBuilderAndAllArgsConstructor() {
        Product product = new Product();
        ProductOption option = new ProductOption();

        // Test Builder và AllArgsConstructor (Lombok)
        ProductOptionValue pov = ProductOptionValue.builder()
                .id(2L)
                .product(product)
                .productOption(option)
                .displayType("size")
                .displayOrder(2)
                .value("XL")
                .build();

        assertNotNull(pov);
        assertEquals(2L, pov.getId());
        assertEquals("XL", pov.getValue());
    }

    @Test
    void testEquals_SameObject() {
        ProductOptionValue pov = new ProductOptionValue();
        pov.setId(1L);
        assertTrue(pov.equals(pov));
    }

    @Test
    void testEquals_DifferentTypeOrNull() {
        ProductOptionValue pov = new ProductOptionValue();
        assertFalse(pov.equals(null));
        assertFalse(pov.equals(new Object()));
    }

    @Test
    void testEquals_SameId() {
        ProductOptionValue pov1 = new ProductOptionValue();
        pov1.setId(10L);
        ProductOptionValue pov2 = new ProductOptionValue();
        pov2.setId(10L);
        
        assertTrue(pov1.equals(pov2));
    }

    @Test
    void testEquals_DifferentId() {
        ProductOptionValue pov1 = new ProductOptionValue();
        pov1.setId(1L);
        ProductOptionValue pov2 = new ProductOptionValue();
        pov2.setId(2L);
        
        assertFalse(pov1.equals(pov2));
    }

    @Test
    void testEquals_NullId() {
        ProductOptionValue pov1 = new ProductOptionValue();
        ProductOptionValue pov2 = new ProductOptionValue();
        
        // id == null -> false
        assertFalse(pov1.equals(pov2));
    }

    @Test
    void testHashCode() {
        ProductOptionValue pov1 = new ProductOptionValue();
        ProductOptionValue pov2 = new ProductOptionValue();
        
        assertEquals(pov1.hashCode(), pov2.hashCode());
        assertEquals(ProductOptionValue.class.hashCode(), pov1.hashCode());
    }
}
