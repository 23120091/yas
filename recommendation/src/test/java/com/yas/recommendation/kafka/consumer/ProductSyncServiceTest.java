package com.yas.recommendation.kafka.consumer;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.yas.commonlibrary.kafka.cdc.message.Operation;
import com.yas.commonlibrary.kafka.cdc.message.Product;
import com.yas.commonlibrary.kafka.cdc.message.ProductCdcMessage;
import com.yas.commonlibrary.kafka.cdc.message.ProductMsgKey;
import com.yas.recommendation.vector.product.service.ProductVectorSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductSyncServiceTest {

    @Mock
    private ProductVectorSyncService productVectorSyncService;

    @InjectMocks
    private ProductSyncService productSyncService;

    private ProductMsgKey key;
    private Product productMessage;

    @BeforeEach
    void setUp() {
        key = new ProductMsgKey(123L);
        productMessage = new Product();
        productMessage.setId(123L);
    }

    @Test
    void sync_NullMessage_CallsDeleteProductVector() {
        // Act
        productSyncService.sync(key, null);

        // Assert
        verify(productVectorSyncService).deleteProductVector(123L);
        verifyNoMoreInteractions(productVectorSyncService);
    }

    @Test
    void sync_DeleteOperation_CallsDeleteProductVector() {
        // Arrange
        ProductCdcMessage cdcMessage = new ProductCdcMessage();
        cdcMessage.setOp(Operation.DELETE);

        // Act
        productSyncService.sync(key, cdcMessage);

        // Assert
        verify(productVectorSyncService).deleteProductVector(123L);
        verifyNoMoreInteractions(productVectorSyncService);
    }

    @Test
    void sync_CreateOperation_CallsCreateProductVector() {
        // Arrange
        ProductCdcMessage cdcMessage = new ProductCdcMessage();
        cdcMessage.setOp(Operation.CREATE);
        cdcMessage.setAfter(productMessage);

        // Act
        productSyncService.sync(key, cdcMessage);

        // Assert
        verify(productVectorSyncService).createProductVector(productMessage);
        verifyNoMoreInteractions(productVectorSyncService);
    }

    @Test
    void sync_UpdateOperation_CallsUpdateProductVector() {
        // Arrange
        ProductCdcMessage cdcMessage = new ProductCdcMessage();
        cdcMessage.setOp(Operation.UPDATE);
        cdcMessage.setAfter(productMessage);

        // Act
        productSyncService.sync(key, cdcMessage);

        // Assert
        verify(productVectorSyncService).updateProductVector(productMessage);
        verifyNoMoreInteractions(productVectorSyncService);
    }

    @Test
    void sync_NullAfter_NoInteractions() {
        // Arrange
        ProductCdcMessage cdcMessage = new ProductCdcMessage();
        cdcMessage.setOp(Operation.CREATE);
        // After is null

        // Act
        productSyncService.sync(key, cdcMessage);

        // Assert
        verifyNoInteractions(productVectorSyncService);
    }
}
