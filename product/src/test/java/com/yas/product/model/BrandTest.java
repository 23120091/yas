package com.yas.product.model;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BrandTest {

    @Test
    void testGettersAndSetters() {
        Brand brand = new Brand();
        List<Product> products = new ArrayList<>();
        products.add(new Product());

        brand.setId(1L);
        brand.setName("Sony");
        brand.setSlug("sony-slug");
        brand.setPublished(true);
        brand.setProducts(products);

        // Kiểm tra các giá trị đã set
        assertEquals(1L, brand.getId());
        assertEquals("Sony", brand.getName());
        assertEquals("sony-slug", brand.getSlug());
        assertTrue(brand.isPublished());
        assertEquals(1, brand.getProducts().size());
    }

    @Test
    void testEquals_SameObject() {
        Brand brand = new Brand();
        brand.setId(1L);
        // Nhánh: this == o
        assertTrue(brand.equals(brand));
    }

    @Test
    void testEquals_DifferentTypeOrNull() {
        Brand brand = new Brand();
        // Nhánh: !(o instanceof Brand) và null
        assertFalse(brand.equals(null));
        assertFalse(brand.equals("Not a Brand"));
    }

    @Test
    void testEquals_SameId() {
        Brand brand1 = new Brand();
        brand1.setId(10L);
        
        Brand brand2 = new Brand();
        brand2.setId(10L);
        
        // Nhánh: id != null && id.equals(...) -> true
        assertTrue(brand1.equals(brand2));
    }

    @Test
    void testEquals_DifferentId() {
        Brand brand1 = new Brand();
        brand1.setId(1L);
        
        Brand brand2 = new Brand();
        brand2.setId(2L);
        
        // Nhánh: id khác nhau -> false
        assertFalse(brand1.equals(brand2));
    }

    @Test
    void testEquals_NullId() {
        Brand brand1 = new Brand();
        Brand brand2 = new Brand();
        
        // Nhánh: id == null -> false
        assertFalse(brand1.equals(brand2));
    }

    @Test
    void testHashCode() {
        Brand brand1 = new Brand();
        Brand brand2 = new Brand();
        
        // HashCode trả về getClass().hashCode() nên phải giống nhau cho cùng 1 class
        assertEquals(brand1.hashCode(), brand2.hashCode());
        assertEquals(Brand.class.hashCode(), brand1.hashCode());
    }
}