package com.yas.recommendation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.yas.recommendation.configuration.RecommendationConfig;
import com.yas.recommendation.viewmodel.ProductDetailVm;
import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private RestClient restClient;

    @Mock
    private RecommendationConfig config;

    @Mock
    private RestClient.RequestHeadersUriSpec<?> requestHeadersUriSpec;

    @Mock
    private RestClient.RequestHeadersSpec<?> requestHeadersSpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    @InjectMocks
    private ProductService productService;

    @BeforeEach
    void setUp() {
        when(config.getApiUrl()).thenReturn("http://localhost:8080/api");
    }

    @Test
    void getProductDetail_Success_ReturnsProductDetailVm() {
        // Arrange
        long productId = 123L;
        ProductDetailVm expectedResponse = new ProductDetailVm(
                productId, "Product Name", "short", "desc", "spec", "sku", "gtin", "slug", 
                true, true, true, true, true, 10.0, 1L, null, 
                "meta", "metaKeyword", "metaDesc", 1L, "Brand", null, null, null, null
        );

        when(restClient.get()).thenReturn((RestClient.RequestHeadersUriSpec) requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(URI.class))).thenReturn((RestClient.RequestHeadersSpec) requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        
        ResponseEntity<ProductDetailVm> responseEntity = ResponseEntity.ok(expectedResponse);
        when(responseSpec.toEntity(any(ParameterizedTypeReference.class))).thenReturn(responseEntity);

        // Act
        ProductDetailVm result = productService.getProductDetail(productId);

        // Assert
        assertNotNull(result);
        assertEquals(productId, result.id());
        assertEquals("Product Name", result.name());
    }
}
