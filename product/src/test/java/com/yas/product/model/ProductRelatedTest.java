package com.yas.product.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class ProductRelatedTest {

    @Test
    void testProductRelatedProperties() {
        // Khởi tạo các object phụ trợ
        Product product = Product.builder().id(1L).name("Product A").build();
        Product relatedProduct = Product.builder().id(2L).name("Product B").build();

        // Test Builder
        ProductRelated relation = ProductRelated.builder()
                .id(100L)
                .product(product)
                .relatedProduct(relatedProduct)
                .build();

        // Verify Getters
        assertEquals(100L, relation.getId());
        assertEquals(1L, relation.getProduct().getId());
        assertEquals(2L, relation.getRelatedProduct().getId());

        // Test Setters
        ProductRelated newRelation = new ProductRelated();
        newRelation.setId(200L);
        assertEquals(200L, newRelation.getId());
    }

    @Test
    void testNoArgsConstructor() {
        ProductRelated relation = new ProductRelated();
        assertNotNull(relation);
    }

    @Test
    void testEqualsAndHashCode() {
        ProductRelated relation1 = new ProductRelated();
        relation1.setId(1L);

        ProductRelated relation2 = new ProductRelated();
        relation2.setId(1L);

        ProductRelated relation3 = new ProductRelated();
        relation3.setId(2L);

        // Test identity
        assertEquals(relation1, relation1);
        
        // Test equality based on ID
        assertEquals(relation1, relation2);
        assertEquals(relation1.hashCode(), relation2.hashCode());

        // Test inequality
        assertNotEquals(relation1, relation3);
        assertNotEquals(relation1, null);
        assertNotEquals(relation1, new Object());
        
        // Test equals with null ID
        ProductRelated relationNullId1 = new ProductRelated();
        ProductRelated relationNullId2 = new ProductRelated();
        
        assertNotEquals(relationNullId1, relationNullId2);
    }
}