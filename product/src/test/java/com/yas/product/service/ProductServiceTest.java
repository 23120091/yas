package com.yas.product.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import org.springframework.data.domain.Page;

import org.springframework.data.domain.Pageable;

import com.yas.commonlibrary.exception.NotFoundException;
import com.yas.product.repository.ProductRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.yas.product.model.Product;
import com.yas.product.model.Brand;
import com.yas.product.viewmodel.product.ProductGetDetailVm;
import com.yas.product.viewmodel.NoFileMediaVm;
import com.yas.product.viewmodel.product.ProductDetailGetVm;
import com.yas.product.viewmodel.product.ProductDetailVm;
import com.yas.product.viewmodel.product.ProductListGetVm;
import com.yas.product.viewmodel.product.ProductFeatureGetVm;
import com.yas.product.viewmodel.product.ProductQuantityPostVm;
import com.yas.product.viewmodel.product.ProductQuantityPutVm;
import com.yas.product.repository.BrandRepository;
import com.yas.product.repository.CategoryRepository;
import com.yas.product.repository.ProductCategoryRepository;
import com.yas.product.repository.ProductImageRepository;
import com.yas.product.repository.ProductOptionRepository;
import com.yas.product.repository.ProductOptionValueRepository;
import com.yas.product.repository.ProductOptionCombinationRepository;
import com.yas.product.repository.ProductRelatedRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List; 
import java.util.Optional;
import org.springframework.data.domain.PageImpl;
import static org.mockito.ArgumentMatchers.any;



@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock private MediaService mediaService;
    @Mock private BrandRepository brandRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private ProductCategoryRepository productCategoryRepository;
    @Mock private ProductImageRepository productImageRepository;
    @Mock private ProductOptionRepository productOptionRepository;
    @Mock private ProductOptionValueRepository productOptionValueRepository;
    @Mock private ProductOptionCombinationRepository productOptionCombinationRepository;
    @Mock private ProductRelatedRepository productRelatedRepository;

    @InjectMocks
    private ProductService productService;

    @Test
    void getRelatedProducts_WhenProductIdNotFound_ShouldThrowNotFoundException() {
        // Giả lập không tìm thấy sản phẩm gốc 
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, 
            () -> productService.getRelatedProductsBackoffice(999L));
    }

    @Test
    void getProduct_WhenNoMedia_ShouldReturnProductWithEmptyMediaList() {
        // 1. Setup
        Product product = Product.builder()
                .id(1L)
                .name("Samsung Galaxy S24")
                .slug("samsung-s24")
                .productImages(new ArrayList<>()) // Dùng đúng tên field trong code của bạn
                .thumbnailMediaId(null)
                .build();

        // Giả lập Repository trả về product này
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        // 2. Execution
        ProductDetailVm result = productService.getProductById(1L);

        // 3. Verification
        assertNotNull(result);
        assertEquals("Samsung Galaxy S24", result.name());
        assertTrue(result.productImageMedias().isEmpty());
    }

    @Test
    void getFeaturedProducts_WhenNoProducts_ShouldReturnEmptyPage() {
        // 1. Setup - Giả lập các tham số đầu vào
        int pageNo = 0;
        int pageSize = 5;
        
        // Tạo một trang trống (Empty Page) đúng kiểu dữ liệu
        // Sử dụng Page.empty() là cách nhanh và chuẩn nhất của Spring Data
        Page<Product> emptyPage = Page.empty();

        // Mock hành vi của Repository
        // Lưu ý: any() giúp bạn không cần lo lắng về việc tạo đối tượng Pageable chính xác
        when(productRepository.getFeaturedProduct(any(Pageable.class)))
                .thenReturn(emptyPage);

        // 2. Execution - Gọi service xử lý
        ProductFeatureGetVm result = productService.getListFeaturedProducts(pageNo, pageSize);

        // 3. Verification - Kiểm tra kết quả
        assertThat(result).isNotNull();
        assertThat(result.productList()).isEmpty();
        
        // Kiểm tra xem repository có thực sự được gọi đúng 1 lần không
        verify(productRepository).getFeaturedProduct(any(Pageable.class));
    }

    @Test
    void getProductById_WhenNotFound_ShouldThrowException() {
        when(productRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
            () -> productService.getProductById(1L));
    }

    @Test
    void getLatestProducts_WhenCountIsZero_ShouldReturnEmptyList() {
        List<?> result = productService.getLatestProducts(0);

        assertThat(result).isEmpty();
    }

    @Test
    void getLatestProducts_WhenNoProducts_ShouldReturnEmptyList() {
        when(productRepository.getLatestProducts(any(Pageable.class)))
            .thenReturn(Collections.emptyList());

        List<?> result = productService.getLatestProducts(5);

        assertThat(result).isEmpty();
    }

    @Test
    void deleteProduct_WhenHasParent_ShouldDeleteOptionCombinations() {
        Product product = Product.builder()
                .id(1L)
                .parent(Product.builder().id(2L).build())
                .build();

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productOptionCombinationRepository.findAllByProduct(product))
                .thenReturn(new ArrayList<>());

        productService.deleteProduct(1L);

        verify(productRepository).save(product);
    }

    @Test
    void deleteProduct_WhenNotFound_ShouldThrowException() {
        when(productRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
            () -> productService.deleteProduct(1L));
    }

    @Test
    void getProductSlug_WhenHasParent_ShouldReturnParentSlug() {
        Product parent = Product.builder().id(2L).slug("parent-slug").build();
        Product product = Product.builder().id(1L).parent(parent).build();

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        var result = productService.getProductSlug(1L);

        assertThat(result.slug()).isEqualTo("parent-slug");
    }

    @Test
    void getProductSlug_WhenNoParent_ShouldReturnOwnSlug() {
        Product product = Product.builder().id(1L).slug("self-slug").build();

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        var result = productService.getProductSlug(1L);

        assertThat(result.slug()).isEqualTo("self-slug");
    }

    @Test
    void getProductsWithFilter_ShouldReturnPageResult() {
        Product product = Product.builder().id(1L).name("Test").build();

        Page<Product> page = new PageImpl<>(List.of(product));

        when(productRepository.getProductsWithFilter(any(), any(), any()))
            .thenReturn(page);

        var result = productService.getProductsWithFilter(0, 10, "a", "b");

        assertThat(result).isNotNull();
        assertThat(result.productContent()).hasSize(1);
    }

    @Test
    void updateProductQuantity_ShouldUpdateStock() {
        Product product = Product.builder().id(1L).stockQuantity(10L).build();

        when(productRepository.findAllByIdIn(any()))
            .thenReturn(List.of(product));

        productService.updateProductQuantity(
            List.of(new ProductQuantityPostVm(1L, 5L))
        );

        assertThat(product.getStockQuantity()).isEqualTo(5L);
        verify(productRepository).saveAll(any());
    }

    @Test
    void subtractStockQuantity_ShouldNotGoBelowZero() {
        Product product = Product.builder()
                .id(1L)
                .stockQuantity(5L)
                .stockTrackingEnabled(true)
                .build();

        when(productRepository.findAllByIdIn(any()))
            .thenReturn(List.of(product));

        productService.subtractStockQuantity(
            List.of(new ProductQuantityPutVm(1L, 10L))
        );

        assertThat(product.getStockQuantity()).isEqualTo(0L);
    }

    @Test
    void getLatestProducts_WhenHasData_ShouldReturnList() {
        Product product = Product.builder().id(1L).name("P1").build();

        when(productRepository.getLatestProducts(any(Pageable.class)))
            .thenReturn(List.of(product));

        var result = productService.getLatestProducts(1);

        assertThat(result).hasSize(1);
    }

    @Test
    void getProductSlug_WhenNotFound_ShouldThrowException() {
        when(productRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
            () -> productService.getProductSlug(1L));
    }

    @Test
    void deleteProduct_WhenNoParent_ShouldOnlySetUnpublished() {
        Product product = Product.builder()
                .id(1L)
                .parent(null)
                .isPublished(true)
                .build();

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        productService.deleteProduct(1L);

        assertThat(product.isPublished()).isFalse();
        verify(productRepository).save(product);
    }

    @Test
    void getProductsWithFilter_WhenEmpty_ShouldReturnEmptyList() {
        when(productRepository.getProductsWithFilter(any(), any(), any()))
            .thenReturn(Page.empty());

        var result = productService.getProductsWithFilter(0, 10, "a", "b");

        assertThat(result.productContent()).isEmpty();
    }

    @Test
    void updateProductQuantity_WithMultipleProducts() {
        Product p1 = Product.builder().id(1L).stockQuantity(10L).build();
        Product p2 = Product.builder().id(2L).stockQuantity(20L).build();

        when(productRepository.findAllByIdIn(any()))
            .thenReturn(List.of(p1, p2));

        productService.updateProductQuantity(List.of(
            new ProductQuantityPostVm(1L, 5L),
            new ProductQuantityPostVm(2L, 15L)
        ));

        assertThat(p1.getStockQuantity()).isEqualTo(5L);
        assertThat(p2.getStockQuantity()).isEqualTo(15L);
    }

    @Test
    void subtractStockQuantity_WhenTrackingDisabled_ShouldNotChange() {
        Product product = Product.builder()
                .id(1L)
                .stockQuantity(10L)
                .stockTrackingEnabled(false)
                .build();

        when(productRepository.findAllByIdIn(any()))
            .thenReturn(List.of(product));

        productService.subtractStockQuantity(
            List.of(new ProductQuantityPutVm(1L, 5L))
        );

        assertThat(product.getStockQuantity()).isEqualTo(10L);
    }

    @Test
    void restoreStockQuantity_ShouldIncreaseStock() {
        Product product = Product.builder()
                .id(1L)
                .stockQuantity(10L)
                .stockTrackingEnabled(true)
                .build();

        when(productRepository.findAllByIdIn(any()))
            .thenReturn(List.of(product));

        productService.restoreStockQuantity(
            List.of(new ProductQuantityPutVm(1L, 5L))
        );

        assertThat(product.getStockQuantity()).isEqualTo(15L);
    }

    @Test
    void getProductById_WithThumbnail_ShouldReturnThumbnail() {
        Product product = Product.builder()
                .id(1L)
                .name("Test")
                .thumbnailMediaId(100L)
                .productImages(new ArrayList<>())
                .build();

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        when(mediaService.getMedia(100L))
            .thenReturn(new NoFileMediaVm(
                100L,
                "image",
                "http://image-url",
                "image/png",
                "1MB"
            ));

        var result = productService.getProductById(1L);

        assertThat(result.thumbnailMedia()).isNotNull();
    }

    // helper
    private NoFileMediaVm media(Long id) {
        return new NoFileMediaVm(id, "name", "url", "type", "size");
    }

    // =========================
    // GET PRODUCTS MULTI QUERY
    // =========================
    @Test
    void getProductsByMultiQuery_ShouldReturnData() {
        Product p = Product.builder()
                .id(1L)
                .name("P1")
                .thumbnailMediaId(100L)
                .price(10.0)
                .build();

        when(productRepository.findByProductNameAndCategorySlugAndPriceBetween(
                any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(p)));

        when(mediaService.getMedia(100L)).thenReturn(media(100L));

        var result = productService.getProductsByMultiQuery(0,10,"a","b",1.0,100.0);

        assertThat(result.productContent()).hasSize(1);
    }

    // =========================
    // PRODUCT VARIATIONS
    // =========================
    @Test
    void getProductVariations_ShouldReturnList() {
        Product child = Product.builder()
                .id(2L)
                .name("child")
                .isPublished(true)
                .build();

        Product parent = Product.builder()
                .id(1L)
                .hasOptions(true)
                .products(List.of(child))
                .build();

        when(productRepository.findById(1L)).thenReturn(Optional.of(parent));
        when(productOptionCombinationRepository.findAllByProduct(child))
                .thenReturn(new ArrayList<>());

        var result = productService.getProductVariationsByParentId(1L);

        assertThat(result).hasSize(1);
    }

    @Test
    void getProductVariations_WhenNoOptions_ShouldReturnEmpty() {
        Product parent = Product.builder()
                .id(1L)
                .hasOptions(false)
                .build();

        when(productRepository.findById(1L)).thenReturn(Optional.of(parent));

        var result = productService.getProductVariationsByParentId(1L);

        assertTrue(result.isEmpty());
    }

    // =========================
    // PRODUCT DETAIL
    // =========================
    @Test
    void getProductDetail_ShouldReturnData() {
        Product product = Product.builder()
                .id(1L)
                .name("Test")
                .slug("test")
                .thumbnailMediaId(100L)
                .productImages(new ArrayList<>())
                .build();

        when(productRepository.findBySlugAndIsPublishedTrue("test"))
                .thenReturn(Optional.of(product));

        when(mediaService.getMedia(100L)).thenReturn(media(100L));

        var result = productService.getProductDetail("test");

        assertNotNull(result);
    }

    @Test
    void getProductDetail_NotFound_ShouldThrow() {
        when(productRepository.findBySlugAndIsPublishedTrue("x"))
                .thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> productService.getProductDetail("x"));
    }

    // =========================
    // EXPORT
    // =========================
    @Test
    void exportProducts_ShouldReturnList() {
        Brand brand = new Brand();
        brand.setId(1L);
        brand.setName("Nike");

        when(brandRepository.findById(1L))
            .thenReturn(Optional.of(brand));

        var result = productService.exportProducts("a","b");

        assertThat(result).hasSize(1);
    }

    // =========================
    // GET BY IDS
    // =========================
    @Test
    void getProductByIds_ShouldReturnList() {
        Product product = Product.builder().id(1L).build();

        when(productRepository.findAllByIdIn(any()))
                .thenReturn(List.of(product));

        var result = productService.getProductByIds(List.of(1L));

        assertThat(result).hasSize(1);
    }

    // =========================
    // STOCK - RESTORE
    // =========================
    @Test
    void restoreStockQuantity_ShouldIncreaseStock_V2() {
        Product product = Product.builder()
                .id(1L)
                .stockQuantity(10L)
                .stockTrackingEnabled(true)
                .build();

        when(productRepository.findAllByIdIn(any()))
                .thenReturn(List.of(product));

        productService.restoreStockQuantity(
                List.of(new ProductQuantityPutVm(1L, 5L))
        );

        assertEquals(15L, product.getStockQuantity());
    }

    @Test
    void restoreStockQuantity_ShouldMergeSameProduct() {
        Product product = Product.builder()
                .id(1L)
                .stockQuantity(10L)
                .stockTrackingEnabled(true)
                .build();

        when(productRepository.findAllByIdIn(any()))
                .thenReturn(List.of(product));

        productService.restoreStockQuantity(List.of(
                new ProductQuantityPutVm(1L, 5L),
                new ProductQuantityPutVm(1L, 5L)
        ));

        assertEquals(20L, product.getStockQuantity());
    }

    // =========================
    // DELETE PRODUCT - WITH COMBINATION
    // =========================
    @Test
    void deleteProduct_WithCombinations_ShouldDeleteThem() {
        Product product = Product.builder()
                .id(1L)
                .parent(Product.builder().id(2L).build())
                .build();

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productOptionCombinationRepository.findAllByProduct(product))
                .thenReturn(List.of(mock(com.yas.product.model.ProductOptionCombination.class)));

        productService.deleteProduct(1L);

        verify(productOptionCombinationRepository).deleteAll(any());
        verify(productRepository).save(product);
    }

    // =========================
    // LATEST PRODUCTS WITH DATA
    // =========================
    @Test
    void getLatestProducts_ShouldReturnData() {
        Product product = Product.builder().id(1L).build();

        when(productRepository.getLatestProducts(any(Pageable.class)))
                .thenReturn(List.of(product));

        var result = productService.getLatestProducts(1);

        assertThat(result).hasSize(1);
    }
}
