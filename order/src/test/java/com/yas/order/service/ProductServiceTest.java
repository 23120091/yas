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

    @Mock private RestClient.RequestHeadersUriSpec<?> getSpec;
    @Mock private RestClient.RequestHeadersSpec<?> headersSpec;
    @Mock private RestClient.RequestBodyUriSpec putSpec;
    @Mock private RestClient.RequestBodySpec bodySpec;
    @Mock private RestClient.ResponseSpec responseSpec;

    @InjectMocks
    private ProductService productService;

    private MockedStatic<AuthenticationUtils> authUtilsMock;

    private static final String JWT_TOKEN = "mock-jwt-token";
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

    @Test
    @SuppressWarnings("unchecked")
    void getProductVariations_ShouldReturnListOfProductVariationVm() {
        List<ProductVariationVm> expected = List.of(
            buildProductVariationVm(1L, "Red"),
            buildProductVariationVm(2L, "Blue")
        );

        when(restClient.get()).thenReturn((RestClient.RequestHeadersUriSpec) getSpec);
        when(getSpec.uri(any(URI.class))).thenReturn((RestClient.RequestHeadersSpec) headersSpec);
        when(headersSpec.headers(any())).thenReturn((RestClient.RequestHeadersSpec) headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toEntity(any(ParameterizedTypeReference.class)))
            .thenReturn(ResponseEntity.ok(expected));

        List<ProductVariationVm> result = productService.getProductVariations(10L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo(1L);
        assertThat(result.get(1).name()).isEqualTo("Blue");
    }

    @Test
    @SuppressWarnings("unchecked")
    void getProductVariations_WhenResponseBodyIsNull_ShouldReturnNull() {
        when(restClient.get()).thenReturn((RestClient.RequestHeadersUriSpec) getSpec);
        when(getSpec.uri(any(URI.class))).thenReturn((RestClient.RequestHeadersSpec) headersSpec);
        when(headersSpec.headers(any())).thenReturn((RestClient.RequestHeadersSpec) headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toEntity(any(ParameterizedTypeReference.class)))
            .thenReturn(ResponseEntity.ok(null));

        List<ProductVariationVm> result = productService.getProductVariations(10L);

        assertThat(result).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void getProductVariations_WhenServiceFails_ShouldPropagate() {
        when(restClient.get()).thenReturn((RestClient.RequestHeadersUriSpec) getSpec);
        when(getSpec.uri(any(URI.class))).thenReturn((RestClient.RequestHeadersSpec) headersSpec);
        when(headersSpec.headers(any())).thenReturn((RestClient.RequestHeadersSpec) headersSpec);
        when(headersSpec.retrieve()).thenThrow(new RuntimeException("product service down"));

        assertThatThrownBy(() -> productService.getProductVariations(10L))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("product service down");
    }

    @Test
    @SuppressWarnings("unchecked")
    void subtractProductStockQuantity_ShouldCallPutEndpointSuccessfully() {
        OrderVm orderVm = buildOrderVm(Set.of(
            buildOrderItemVm(1L, 2),
            buildOrderItemVm(2L, 3)
        ));

        when(restClient.put()).thenReturn(putSpec);
        when(putSpec.uri(any(URI.class))).thenReturn(bodySpec);
        when(bodySpec.headers(any())).thenReturn(bodySpec);
        when(bodySpec.body(any())).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);

        productService.subtractProductStockQuantity(orderVm);

        verify(restClient).put();
        verify(bodySpec).body(any());
        verify(bodySpec).retrieve();
    }

    @Test
    @SuppressWarnings("unchecked")
    void subtractProductStockQuantity_WhenServiceFails_ShouldPropagate() {
        OrderVm orderVm = buildOrderVm(Set.of(buildOrderItemVm(1L, 1)));

        when(restClient.put()).thenReturn(putSpec);
        when(putSpec.uri(any(URI.class))).thenReturn(bodySpec);
        when(bodySpec.headers(any())).thenReturn(bodySpec);
        when(bodySpec.body(any())).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenThrow(new RuntimeException("subtract stock failed"));

        assertThatThrownBy(() -> productService.subtractProductStockQuantity(orderVm))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("subtract stock failed");
    }

    @Test
    @SuppressWarnings("unchecked")
    void subtractProductStockQuantity_WithEmptyOrderItems_ShouldStillCallEndpoint() {
        OrderVm orderVm = buildOrderVm(Set.of());

        when(restClient.put()).thenReturn(putSpec);
        when(putSpec.uri(any(URI.class))).thenReturn(bodySpec);
        when(bodySpec.headers(any())).thenReturn(bodySpec);
        when(bodySpec.body(any())).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);

        productService.subtractProductStockQuantity(orderVm);

        verify(bodySpec).body(argThat(body -> body instanceof List && ((List<?>) body).isEmpty()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getProductInfomation_ShouldReturnMapKeyedByProductId() {
        ProductCheckoutListVm product1 = buildProductCheckoutListVm(1L, "Product A");
        ProductCheckoutListVm product2 = buildProductCheckoutListVm(2L, "Product B");
        ProductGetCheckoutListVm response = new ProductGetCheckoutListVm(List.of(product1, product2), 1, 1, 2, 2, false);

        when(restClient.get()).thenReturn((RestClient.RequestHeadersUriSpec) getSpec);
        when(getSpec.uri(any(URI.class))).thenReturn((RestClient.RequestHeadersSpec) headersSpec);
        when(headersSpec.headers(any())).thenReturn((RestClient.RequestHeadersSpec) headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toEntity(any(ParameterizedTypeReference.class)))
            .thenReturn(ResponseEntity.ok(response));

        Map<Long, ProductCheckoutListVm> result =
            productService.getProductInfomation(Set.of(1L, 2L), 0, 10);

        assertThat(result).hasSize(2);
        assertThat(result).containsKey(1L);
        assertThat(result).containsKey(2L);
        assertThat(result.get(1L).getName()).isEqualTo("Product A");
    }

    @Test
    @SuppressWarnings("unchecked")
    void getProductInfomation_WhenResponseIsNull_ShouldThrowNotFoundException() {
        when(restClient.get()).thenReturn((RestClient.RequestHeadersUriSpec) getSpec);
        when(getSpec.uri(any(URI.class))).thenReturn((RestClient.RequestHeadersSpec) headersSpec);
        when(headersSpec.headers(any())).thenReturn((RestClient.RequestHeadersSpec) headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toEntity(any(ParameterizedTypeReference.class)))
            .thenReturn(ResponseEntity.ok(null));

        assertThatThrownBy(() -> productService.getProductInfomation(Set.of(1L), 0, 10))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("PRODUCT_NOT_FOUND");
    }

    @Test
    @SuppressWarnings("unchecked")
    void getProductInfomation_WhenProductListIsNull_ShouldThrowNotFoundException() {
        ProductGetCheckoutListVm response = new ProductGetCheckoutListVm(null, 1, 1, 2, 2, false);

        when(restClient.get()).thenReturn((RestClient.RequestHeadersUriSpec) getSpec);
        when(getSpec.uri(any(URI.class))).thenReturn((RestClient.RequestHeadersSpec) headersSpec);
        when(headersSpec.headers(any())).thenReturn((RestClient.RequestHeadersSpec) headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toEntity(any(ParameterizedTypeReference.class)))
            .thenReturn(ResponseEntity.ok(response));

        assertThatThrownBy(() -> productService.getProductInfomation(Set.of(1L), 0, 10))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("PRODUCT_NOT_FOUND");
    }

    @Test
    @SuppressWarnings("unchecked")
    void getProductInfomation_WhenServiceFails_ShouldPropagate() {
        when(restClient.get()).thenReturn((RestClient.RequestHeadersUriSpec) getSpec);
        when(getSpec.uri(any(URI.class))).thenReturn((RestClient.RequestHeadersSpec) headersSpec);
        when(headersSpec.headers(any())).thenReturn((RestClient.RequestHeadersSpec) headersSpec);
        when(headersSpec.retrieve()).thenThrow(new RuntimeException("product info service down"));

        assertThatThrownBy(() -> productService.getProductInfomation(Set.of(1L), 0, 10))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("product info service down");
    }

    @Test
    @SuppressWarnings("unchecked")
    void getProductInfomation_ShouldCollectResultsIntoMapCorrectly() {
        ProductCheckoutListVm product = buildProductCheckoutListVm(5L, "Single Product");
        ProductGetCheckoutListVm response = new ProductGetCheckoutListVm(List.of(product), 1, 1, 1, 1, false);

        when(restClient.get()).thenReturn((RestClient.RequestHeadersUriSpec) getSpec);
        when(getSpec.uri(any(URI.class))).thenReturn((RestClient.RequestHeadersSpec) headersSpec);
        when(headersSpec.headers(any())).thenReturn((RestClient.RequestHeadersSpec) headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toEntity(any(ParameterizedTypeReference.class)))
            .thenReturn(ResponseEntity.ok(response));

        Map<Long, ProductCheckoutListVm> result =
            productService.getProductInfomation(Set.of(5L), 0, 5);

        assertThat(result).hasSize(1);
        assertThat(result.get(5L).getName()).isEqualTo("Single Product");
    }

    @Test
    void handleProductVariationListFallback_ShouldRethrowException() {
        RuntimeException ex = new RuntimeException("cb open");

        assertThatThrownBy(() -> productService.handleProductVariationListFallback(ex))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("cb open");
    }

    @Test
    void handleProductInfomationFallback_ShouldRethrowException() {
        RuntimeException ex = new RuntimeException("cb open");

        assertThatThrownBy(() -> productService.handleProductInfomationFallback(ex))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("cb open");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private ProductVariationVm buildProductVariationVm(Long id, String name) {
        return new ProductVariationVm(id, name, "SKU-DUMMY");
    }

    private ProductCheckoutListVm buildProductCheckoutListVm(Long id, String name) {
        ProductCheckoutListVm vm = new ProductCheckoutListVm();
        vm.setId(id);
        vm.setName(name);
        return vm;
    }

    private OrderItemVm buildOrderItemVm(Long productId, int quantity) {
        return new OrderItemVm(
            productId, 1L, "Product Name", quantity,
            BigDecimal.ZERO, "Note",
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
}