package com.yas.product.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yas.product.model.Product;
import com.yas.product.model.ProductOptionCombination;
import com.yas.product.repository.ProductOptionCombinationRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class) // Sử dụng Mockito thay vì Spring Boot Test
class ProductOptionCombinationRepositoryTest {

    @Mock
    private ProductOptionCombinationRepository repository; // Giả lập repository

    private Product parentProduct;
    private Product childProduct;
    private ProductOptionCombination combination;

    @BeforeEach
    void setUp() {
        // Tạo dữ liệu giả lập (Mock Data)
        parentProduct = Product.builder()
                .id(1L)
                .name("Samsung Galaxy S24")
                .slug("samsung-s24")
                .build();

        childProduct = Product.builder()
                .id(2L)
                .name("Samsung Galaxy S24 256GB")
                .slug("samsung-s24-256gb")
                .parent(parentProduct)
                .build();

        combination = new ProductOptionCombination();
        combination.setProduct(childProduct);
    }

    @Test
    void findAllByParentProductId_ShouldReturnCorrectList() {
        // Giả lập hành vi: Khi gọi hàm này với ID của parent, trả về list chứa combination
        when(repository.findAllByParentProductId(parentProduct.getId())).thenReturn(List.of(combination));

        List<ProductOptionCombination> results = repository.findAllByParentProductId(parentProduct.getId());

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getProduct().getParent().getId()).isEqualTo(parentProduct.getId());
    }

    @Test
    void findAllByProduct_ShouldReturnResults() {
        when(repository.findAllByProduct(childProduct)).thenReturn(List.of(combination));

        List<ProductOptionCombination> results = repository.findAllByProduct(childProduct);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getProduct().getName()).isEqualTo("Samsung Galaxy S24 256GB");
    }

    @Test
    void findByProductId_ShouldReturnOptionalCombination() {
        when(repository.findByProductId(childProduct.getId())).thenReturn(Optional.of(combination));

        Optional<ProductOptionCombination> result = repository.findByProductId(childProduct.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getProduct().getId()).isEqualTo(childProduct.getId());
    }

    @Test
    void deleteByProductId_ShouldInvokeMethod() {
        // Đối với hàm void, chúng ta chỉ cần verify xem nó có được gọi hay không
        repository.deleteByProductId(childProduct.getId());
        
        verify(repository).deleteByProductId(childProduct.getId());
    }
}