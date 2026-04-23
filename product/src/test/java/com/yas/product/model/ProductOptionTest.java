package com.yas.product.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProductOptionTest {

    @Test
    void testEquals_SameObject() {
        ProductOption option = new ProductOption();
        option.setId(1L);
        
        // Nhánh 1: if (this == o) -> Trả về true
        assertTrue(option.equals(option));
    }

    @Test
    void testEquals_DifferentType() {
        ProductOption option = new ProductOption();
        
        // Nhánh 2: if (!(o instanceof ProductOption)) -> Trả về false
        assertFalse(option.equals("Not a ProductOption object"));
        assertFalse(option.equals(null));
    }

    @Test
    void testEquals_DifferentId() {
        ProductOption option1 = new ProductOption();
        option1.setId(1L);
        
        ProductOption option2 = new ProductOption();
        option2.setId(2L);
        
        // Nhánh 3: id khác nhau -> Trả về false
        assertFalse(option1.equals(option2));
    }

    @Test
    void testEquals_SameId() {
        ProductOption option1 = new ProductOption();
        option1.setId(10L);
        
        ProductOption option2 = new ProductOption();
        option2.setId(10L);
        
        // Nhánh 3: id giống nhau -> Trả về true
        assertTrue(option1.equals(option2));
    }

    @Test
    void testEquals_NullId() {
        ProductOption option1 = new ProductOption();
        ProductOption option2 = new ProductOption();
        
        // Nhánh 3: id == null -> Trả về false (theo logic return id != null && ...)
        assertFalse(option1.equals(option2));
    }

    @Test
    void testHashCode() {
        ProductOption option1 = new ProductOption();
        ProductOption option2 = new ProductOption();
        
        // Kiểm tra hashCode: Theo code của bạn, nó trả về getClass().hashCode()
        // Nên mọi instance của cùng một class phải có hashCode giống nhau
        assertEquals(option1.hashCode(), option2.hashCode());
        assertEquals(ProductOption.class.hashCode(), option1.hashCode());
    }
}
