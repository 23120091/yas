package com.yas.inventory.service;

import static com.yas.inventory.util.SecurityContextUtils.setUpSecurityContext;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yas.commonlibrary.exception.BadRequestException;
import com.yas.commonlibrary.exception.NotFoundException;
import com.yas.commonlibrary.exception.StockExistingException;
import com.yas.inventory.model.Stock;
import com.yas.inventory.model.Warehouse;
import com.yas.inventory.repository.StockRepository;
import com.yas.inventory.repository.WarehouseRepository;
import com.yas.inventory.viewmodel.product.ProductInfoVm;
import com.yas.inventory.viewmodel.stock.StockPostVm;
import com.yas.inventory.viewmodel.stock.StockQuantityUpdateVm;
import com.yas.inventory.viewmodel.stock.StockQuantityVm;
import com.yas.inventory.viewmodel.stock.StockVm;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class StockServiceTest {

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private StockRepository stockRepository;

    @Mock
    private ProductService productService;

    @Mock
    private WarehouseService warehouseService;

    @Mock
    private StockHistoryService stockHistoryService;

    @InjectMocks
    private StockService stockService;

    private Warehouse warehouse;
    private Stock stock;
    private ProductInfoVm productInfoVm;

    @BeforeEach
    void setUp() {
        // setUpSecurityContext("test");
        warehouse = new Warehouse();
        warehouse.setId(1L);
        warehouse.setName("Warehouse 1");

        stock = Stock.builder()
            .id(1L)
            .productId(1L)
            .warehouse(warehouse)
            .quantity(100L)
            .reservedQuantity(0L)
            .build();

        productInfoVm = new ProductInfoVm(1L, "Product 1", "SKU001", false);
    }

    @Test
    void addProductIntoWarehouse_Success_AddsSingleProduct() {
        StockPostVm postVm = new StockPostVm(1L, 1L);

        when(stockRepository.existsByWarehouseIdAndProductId(1L, 1L)).thenReturn(false);
        when(productService.getProduct(1L)).thenReturn(productInfoVm);
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
        when(stockRepository.saveAll(anyList())).thenReturn(List.of(stock));

        assertDoesNotThrow(() -> stockService.addProductIntoWarehouse(List.of(postVm)));

        verify(stockRepository).saveAll(anyList());
    }

    @Test
    void addProductIntoWarehouse_StockExists_ThrowsStockExistingException() {
        StockPostVm postVm = new StockPostVm(1L, 1L);

        when(stockRepository.existsByWarehouseIdAndProductId(1L, 1L)).thenReturn(true);

        StockExistingException exception = assertThrows(StockExistingException.class,
            () -> stockService.addProductIntoWarehouse(List.of(postVm)));

        assertNotNull(exception);
    }

    @Test
    void addProductIntoWarehouse_ProductNotFound_ThrowsNotFoundException() {
        StockPostVm postVm = new StockPostVm(1L, 1L);

        when(stockRepository.existsByWarehouseIdAndProductId(1L, 1L)).thenReturn(false);
        when(productService.getProduct(1L)).thenReturn(null);

        NotFoundException exception = assertThrows(NotFoundException.class,
            () -> stockService.addProductIntoWarehouse(List.of(postVm)));

        assertNotNull(exception);
    }

    @Test
    void addProductIntoWarehouse_WarehouseNotFound_ThrowsNotFoundException() {
        StockPostVm postVm = new StockPostVm(1L, 1L);

        when(stockRepository.existsByWarehouseIdAndProductId(1L, 1L)).thenReturn(false);
        when(productService.getProduct(1L)).thenReturn(productInfoVm);
        when(warehouseRepository.findById(1L)).thenReturn(Optional.empty());

        NotFoundException exception = assertThrows(NotFoundException.class,
            () -> stockService.addProductIntoWarehouse(List.of(postVm)));

        assertNotNull(exception);
    }

    @Test
    void addProductIntoWarehouse_MultipleProducts_Success() {
        StockPostVm postVm1 = new StockPostVm(1L, 1L);
        StockPostVm postVm2 = new StockPostVm(2L, 1L);

        when(stockRepository.existsByWarehouseIdAndProductId(1L, 1L)).thenReturn(false);
        when(stockRepository.existsByWarehouseIdAndProductId(1L, 2L)).thenReturn(false);
        when(productService.getProduct(anyLong())).thenReturn(productInfoVm);
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
        when(stockRepository.saveAll(anyList())).thenReturn(List.of(stock, stock));

        assertDoesNotThrow(() -> stockService.addProductIntoWarehouse(List.of(postVm1, postVm2)));

        verify(stockRepository).saveAll(anyList());
    }

    @Test
    void getStocksByWarehouseIdAndProductNameAndSku_Success() {
        List<ProductInfoVm> products = List.of(productInfoVm);
        List<Stock> stocks = List.of(stock);

        when(warehouseService.getProductWarehouse(1L, "Product 1", "SKU001", com.yas.inventory.model.enumeration.FilterExistInWhSelection.YES))
            .thenReturn(products);
        when(stockRepository.findByWarehouseIdAndProductIdIn(1L, List.of(1L)))
            .thenReturn(stocks);

        List<StockVm> result = stockService.getStocksByWarehouseIdAndProductNameAndSku(1L, "Product 1", "SKU001");

        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    void getStocksByWarehouseIdAndProductNameAndSku_NoStocks_ReturnsEmptyList() {
        when(warehouseService.getProductWarehouse(1L, "Product 1", "SKU001", com.yas.inventory.model.enumeration.FilterExistInWhSelection.YES))
            .thenReturn(List.of());
        when(stockRepository.findByWarehouseIdAndProductIdIn(1L, List.of()))
            .thenReturn(List.of());

        List<StockVm> result = stockService.getStocksByWarehouseIdAndProductNameAndSku(1L, "Product 1", "SKU001");

        assertNotNull(result);
        assertEquals(0, result.size());
    }

    @Test
    void updateProductQuantityInStock_Success_IncreaseQuantity() {
        StockQuantityVm stockQuantityVm = new StockQuantityVm(1L, 50L, "Add stock");
        StockQuantityUpdateVm updateVm = new StockQuantityUpdateVm(List.of(stockQuantityVm));

        Stock existingStock = Stock.builder()
            .id(1L)
            .productId(1L)
            .warehouse(warehouse)
            .quantity(100L)
            .reservedQuantity(0L)
            .build();

        when(stockRepository.findAllById(List.of(1L))).thenReturn(List.of(existingStock));
        when(stockRepository.saveAll(anyList())).thenReturn(List.of(existingStock));

        assertDoesNotThrow(() -> stockService.updateProductQuantityInStock(updateVm));

        verify(stockRepository).saveAll(anyList());
        verify(stockHistoryService).createStockHistories(anyList(), anyList());
    }

    @Test
    void updateProductQuantityInStock_NegativeQuantity_IncreaseQuantity() {
        StockQuantityVm stockQuantityVm = new StockQuantityVm(1L, -30L, "Remove stock");
        StockQuantityUpdateVm updateVm = new StockQuantityUpdateVm(List.of(stockQuantityVm));

        Stock existingStock = Stock.builder()
            .id(1L)
            .productId(1L)
            .warehouse(warehouse)
            .quantity(100L)
            .reservedQuantity(0L)
            .build();

        when(stockRepository.findAllById(List.of(1L))).thenReturn(List.of(existingStock));
        when(stockRepository.saveAll(anyList())).thenReturn(List.of(existingStock));

        assertDoesNotThrow(() -> stockService.updateProductQuantityInStock(updateVm));

        verify(stockRepository).saveAll(anyList());
    }

    @Test
    void updateProductQuantityInStock_InvalidAdjustmentNegativeGreaterThanCurrent_ThrowsBadRequestException() {
        StockQuantityVm stockQuantityVm = new StockQuantityVm(1L, -150L, "Invalid removal");
        StockQuantityUpdateVm updateVm = new StockQuantityUpdateVm(List.of(stockQuantityVm));

        Stock existingStock = Stock.builder()
            .id(1L)
            .productId(1L)
            .warehouse(warehouse)
            .quantity(100L)
            .reservedQuantity(0L)
            .build();

        when(stockRepository.findAllById(List.of(1L))).thenReturn(List.of(existingStock));

        BadRequestException exception = assertThrows(BadRequestException.class,
            () -> stockService.updateProductQuantityInStock(updateVm));

        assertNotNull(exception);
    }

    @Test
    void updateProductQuantityInStock_ZeroQuantity_NoChangeInQuantity() {
        StockQuantityVm stockQuantityVm = new StockQuantityVm(1L, 0L, "No change");
        StockQuantityUpdateVm updateVm = new StockQuantityUpdateVm(List.of(stockQuantityVm));

        Stock existingStock = Stock.builder()
            .id(1L)
            .productId(1L)
            .warehouse(warehouse)
            .quantity(100L)
            .reservedQuantity(0L)
            .build();

        when(stockRepository.findAllById(List.of(1L))).thenReturn(List.of(existingStock));
        when(stockRepository.saveAll(anyList())).thenReturn(List.of(existingStock));

        assertDoesNotThrow(() -> stockService.updateProductQuantityInStock(updateVm));

        verify(stockRepository).saveAll(anyList());
    }

    @Test
    void updateProductQuantityInStock_MultipleStocks_Success() {
        StockQuantityVm stockQuantityVm1 = new StockQuantityVm(1L, 50L, "Add stock");
        StockQuantityVm stockQuantityVm2 = new StockQuantityVm(2L, 30L, "Add stock");
        StockQuantityUpdateVm updateVm = new StockQuantityUpdateVm(List.of(stockQuantityVm1, stockQuantityVm2));

        Stock stock1 = Stock.builder()
            .id(1L)
            .productId(1L)
            .warehouse(warehouse)
            .quantity(100L)
            .reservedQuantity(0L)
            .build();

        Stock stock2 = Stock.builder()
            .id(2L)
            .productId(2L)
            .warehouse(warehouse)
            .quantity(50L)
            .reservedQuantity(0L)
            .build();

        when(stockRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(stock1, stock2));
        when(stockRepository.saveAll(anyList())).thenReturn(List.of(stock1, stock2));

        assertDoesNotThrow(() -> stockService.updateProductQuantityInStock(updateVm));

        verify(stockRepository).saveAll(anyList());
        verify(stockHistoryService).createStockHistories(anyList(), anyList());
    }

    @Test
    void updateProductQuantityInStock_NullQuantity_TreatsAsZero() {
        StockQuantityVm stockQuantityVm = new StockQuantityVm(1L, null, "Note");
        StockQuantityUpdateVm updateVm = new StockQuantityUpdateVm(List.of(stockQuantityVm));

        Stock existingStock = Stock.builder()
            .id(1L)
            .productId(1L)
            .warehouse(warehouse)
            .quantity(100L)
            .reservedQuantity(0L)
            .build();

        when(stockRepository.findAllById(List.of(1L))).thenReturn(List.of(existingStock));
        when(stockRepository.saveAll(anyList())).thenReturn(List.of(existingStock));

        assertDoesNotThrow(() -> stockService.updateProductQuantityInStock(updateVm));

        verify(stockRepository).saveAll(anyList());
    }
}
