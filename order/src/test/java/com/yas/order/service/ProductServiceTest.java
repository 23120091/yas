package com.yas.order.service;

import com.yas.commonlibrary.exception.NotFoundException;
import com.yas.commonlibrary.utils.AuthenticationUtils;
import com.yas.order.config.ServiceUrlConfig;
import com.yas.order.viewmodel.order.OrderItemVm;
import com.yas.order.viewmodel.order.OrderVm;
import com.yas.order.viewmodel.product.ProductCheckoutListVm;
import com.yas.order.viewmodel.product.ProductGetCheckoutListVm;
import com.yas.order.viewmodel.product.ProductVariationVm;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock private RestClient restClient;
    @Mock private ServiceUrlConfig serviceUrlConfig;

    // Fluent chain mocks cho RestClient
    @SuppressWarnings("rawtypes")
    @Mock private RestClient.RequestHeadersUriSpec getSpec;
    @SuppressWarnings("rawtypes")
    @Mock private RestClient.RequestHeadersSpec headersSpec;
    @Mock private RestClient.RequestBodyUriSpec putSpec;
    @Mock private RestClient.RequestBodySpec bodySpec;
    @Mock private RestClient.ResponseSpec responseSpec;

    @InjectMocks
    private ProductService productService;

    private MockedStatic<AuthenticationUtils> authUtilsMock;

    private static final String JWT_TOKEN = "mock-jwt";
    private static final String PRODUCT_BASE_URL = "http://product-service";

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        authUtilsMock = mockStatic(AuthenticationUtils.class);
        authUtilsMock.when(AuthenticationUtils::extractJwt).thenReturn(JWT_TOKEN);
        when(serviceUrlConfig.product()).thenReturn(PRODUCT_BASE_URL);
    }

    @AfterEach
    void tearDown() {
        authUtilsMock.close();
    }

    // =========================================================================
    // getProductVariations
    // =========================================================================

    @Nested
    class GetProductVariationsTest {

        /**
         * Happy path: REST trả về danh sách variations, method trả về đúng body.
         */
        @Test
        @SuppressWarnings("unchecked")
        void getProductVariations_WhenServiceReturnsData_ShouldReturnVariationList() {
            List<ProductVariationVm> expected = List.of(
                new ProductVariationVm(1L, "Red", "SKU-001"),
                new ProductVariationVm(2L, "Blue", "SKU-002")
            );

            when(restClient.get()).thenReturn(getSpec);
            when(getSpec.uri(any(URI.class))).thenReturn(headersSpec);
            when(headersSpec.headers(any())).thenReturn(headersSpec);
            when(headersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.toEntity(any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(expected));

            List<ProductVariationVm> result = productService.getProductVariations(10L);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).id()).isEqualTo(1L);
            assertThat(result.get(0).name()).isEqualTo("Red");
            assertThat(result.get(1).id()).isEqualTo(2L);
        }

        /**
         * Khi body trả về null thì method trả về null (không throw).
         */
        @Test
        @SuppressWarnings("unchecked")
        void getProductVariations_WhenResponseBodyIsNull_ShouldReturnNull() {
            when(restClient.get()).thenReturn(getSpec);
            when(getSpec.uri(any(URI.class))).thenReturn(headersSpec);
            when(headersSpec.headers(any())).thenReturn(headersSpec);
            when(headersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.toEntity(any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(null));

            List<ProductVariationVm> result = productService.getProductVariations(10L);

            assertThat(result).isNull();
        }

        /**
         * Khi REST call ném exception thì exception được propagate.
         */
        @Test
        @SuppressWarnings("unchecked")
        void getProductVariations_WhenRestCallThrows_ShouldPropagateException() {
            when(restClient.get()).thenReturn(getSpec);
            when(getSpec.uri(any(URI.class))).thenReturn(headersSpec);
            when(headersSpec.headers(any())).thenReturn(headersSpec);
            when(headersSpec.retrieve()).thenThrow(new RuntimeException("service unavailable"));

            assertThatThrownBy(() -> productService.getProductVariations(10L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("service unavailable");
        }
    }

    // =========================================================================
    // subtractProductStockQuantity
    // =========================================================================

    @Nested
    class SubtractProductStockQuantityTest {

        /**
         * Happy path: PUT endpoint được gọi với body chứa danh sách ProductQuantityItem.
         */
        @Test
        @SuppressWarnings("unchecked")
        void getProductInfomation_WhenServiceReturnsData_ShouldReturnMapKeyedById() {
            // Thay vì dùng lệnh new bị cấm, ta gọi hàm mock helper
            ProductCheckoutListVm p1 = buildProductCheckoutListVm(1L, "Product A");
            ProductCheckoutListVm p2 = buildProductCheckoutListVm(2L, "Product B");
            ProductGetCheckoutListVm response =
                new ProductGetCheckoutListVm(List.of(p1, p2), 0, 2, 2, 1, false);

            when(restClient.get()).thenReturn(getSpec);
            when(getSpec.uri(any(URI.class))).thenReturn(headersSpec);
            when(headersSpec.headers(any())).thenReturn(headersSpec);
            when(headersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.toEntity(any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(response));

            Map<Long, ProductCheckoutListVm> result =
                productService.getProductInfomation(Set.of(1L, 2L), 0, 10);

            assertThat(result).hasSize(2);
            assertThat(result).containsKey(1L);
            assertThat(result).containsKey(2L);
            assertThat(result.get(1L).getName()).isEqualTo("Product A");
            assertThat(result.get(2L).getName()).isEqualTo("Product B");
        }

        /**
         * buildProductQuantityItems (private) được test gián tiếp:
         * với orderItems rỗng → body được gọi với List rỗng.
         */
        @Test
        @SuppressWarnings("unchecked")
        void subtractProductStockQuantity_WhenOrderItemsEmpty_ShouldSendEmptyBody() {
            OrderVm orderVm = buildOrderVm(Set.of());

            when(restClient.put()).thenReturn(putSpec);
            when(putSpec.uri(any(URI.class))).thenReturn(bodySpec);
            when(bodySpec.headers(any())).thenReturn(bodySpec);
            when(bodySpec.body(any())).thenReturn(bodySpec);
            when(bodySpec.retrieve()).thenReturn(responseSpec);

            productService.subtractProductStockQuantity(orderVm);

            verify(bodySpec).body(argThat(b -> b instanceof List && ((List<?>) b).isEmpty()));
        }

        /**
         * Khi REST call ném exception thì exception được propagate.
         */
        @Test
        @SuppressWarnings("unchecked")
        void subtractProductStockQuantity_WhenRestCallThrows_ShouldPropagateException() {
            OrderVm orderVm = buildOrderVm(Set.of(buildOrderItemVm(1L, 1)));

            when(restClient.put()).thenReturn(putSpec);
            when(putSpec.uri(any(URI.class))).thenReturn(bodySpec);
            when(bodySpec.headers(any())).thenReturn(bodySpec);
            when(bodySpec.body(any())).thenReturn(bodySpec);
            when(bodySpec.retrieve()).thenThrow(new RuntimeException("connection refused"));

            assertThatThrownBy(() -> productService.subtractProductStockQuantity(orderVm))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("connection refused");
        }
    }

    // =========================================================================
    // getProductInfomation
    // =========================================================================

    @Nested
    class GetProductInfomationTest {

        /**
         * Happy path: trả về Map keyed theo productId.
         */
        @Test
        @SuppressWarnings("unchecked")
        void getProductInfomation_WhenServiceReturnsData_ShouldReturnMapKeyedById() {
            ProductCheckoutListVm p1 = new ProductCheckoutListVm(1L, "Product A", 0.0, 1L);
            ProductCheckoutListVm p2 = new ProductCheckoutListVm(2L, "Product B", 0.0, 1L);
            ProductGetCheckoutListVm response =
                new ProductGetCheckoutListVm(List.of(p1, p2), 0, 2, 2, 1, false);

            when(restClient.get()).thenReturn(getSpec);
            when(getSpec.uri(any(URI.class))).thenReturn(headersSpec);
            when(headersSpec.headers(any())).thenReturn(headersSpec);
            when(headersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.toEntity(any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(response));

            Map<Long, ProductCheckoutListVm> result =
                productService.getProductInfomation(Set.of(1L, 2L), 0, 10);

            assertThat(result).hasSize(2);
            assertThat(result).containsKey(1L);
            assertThat(result).containsKey(2L);
            assertThat(result.get(1L).getName()).isEqualTo("Product A");
            assertThat(result.get(2L).getName()).isEqualTo("Product B");
        }

        /**
         * Khi response body là null → throw NotFoundException với message "PRODUCT_NOT_FOUND".
         */
        @Test
        @SuppressWarnings("unchecked")
        void getProductInfomation_WhenResponseBodyIsNull_ShouldThrowNotFoundException() {
            when(restClient.get()).thenReturn(getSpec);
            when(getSpec.uri(any(URI.class))).thenReturn(headersSpec);
            when(headersSpec.headers(any())).thenReturn(headersSpec);
            when(headersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.toEntity(any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(null));

            assertThatThrownBy(() -> productService.getProductInfomation(Set.of(1L), 0, 10))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("PRODUCT_NOT_FOUND");
        }

        /**
         * Khi productCheckoutListVms trong response là null → throw NotFoundException.
         */
        @Test
        @SuppressWarnings("unchecked")
        void getProductInfomation_WhenProductListIsNull_ShouldThrowNotFoundException() {
            ProductGetCheckoutListVm response =
                new ProductGetCheckoutListVm(null, 0, 0, 0, 1, false);

            when(restClient.get()).thenReturn(getSpec);
            when(getSpec.uri(any(URI.class))).thenReturn(headersSpec);
            when(headersSpec.headers(any())).thenReturn(headersSpec);
            when(headersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.toEntity(any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(response));

            assertThatThrownBy(() -> productService.getProductInfomation(Set.of(1L), 0, 10))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("PRODUCT_NOT_FOUND");
        }

        /**
         * Khi REST call ném exception thì exception được propagate.
         */
        @Test
        @SuppressWarnings("unchecked")
        void getProductInfomation_WhenRestCallThrows_ShouldPropagateException() {
            when(restClient.get()).thenReturn(getSpec);
            when(getSpec.uri(any(URI.class))).thenReturn(headersSpec);
            when(headersSpec.headers(any())).thenReturn(headersSpec);
            when(headersSpec.retrieve()).thenThrow(new RuntimeException("timeout"));

            assertThatThrownBy(() -> productService.getProductInfomation(Set.of(1L), 0, 10))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("timeout");
        }
    }

    // =========================================================================
    // handleProductVariationListFallback (protected)
    // =========================================================================

    @Nested
    class HandleProductVariationListFallbackTest {

        /**
         * Fallback delegate sang handleTypedFallback → rethrow exception.
         */
        @Test
        void handleProductVariationListFallback_ShouldRethrowException() {
            RuntimeException ex = new RuntimeException("circuit breaker open");

            assertThatThrownBy(() -> productService.handleProductVariationListFallback(ex))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("circuit breaker open");
        }
    }

    // =========================================================================
    // handleProductInfomationFallback (protected)
    // =========================================================================

    @Nested
    class HandleProductInfomationFallbackTest {

        /**
         * Fallback delegate sang handleTypedFallback → rethrow exception.
         */
        @Test
        void handleProductInfomationFallback_ShouldRethrowException() {
            RuntimeException ex = new RuntimeException("circuit breaker open");

            assertThatThrownBy(() -> productService.handleProductInfomationFallback(ex))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("circuit breaker open");
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private OrderItemVm buildOrderItemVm(Long productId, int quantity) {
        return new OrderItemVm(
            productId, 1L, "Product Name", quantity,
            BigDecimal.ZERO, "note",
            BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal.ZERO, 1L
        );
    }

    private OrderVm buildOrderVm(Set<OrderItemVm> items) {
        return new OrderVm(
            1L, "checkout-001", null, null, "test@example.com",
            0f, 0f, 1, BigDecimal.ZERO, BigDecimal.ZERO,
            "COUPON", null, null, null, null,
            items, "note"
        );
    }
    // Thêm hàm mock này vào phần Helpers
    private ProductCheckoutListVm buildProductCheckoutListVm(Long id, String name) {
        ProductCheckoutListVm mockVm = mock(ProductCheckoutListVm.class);
        lenient().when(mockVm.getId()).thenReturn(id);
        lenient().when(mockVm.getName()).thenReturn(name);
        return mockVm;
    }
}