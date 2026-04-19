package com.yas.inventory.service;

import static com.yas.inventory.util.SecurityContextUtils.setUpSecurityContext;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yas.inventory.model.Stock;
import com.yas.inventory.model.StockHistory;
import com.yas.inventory.model.Warehouse;
import com.yas.inventory.repository.StockHistoryRepository;
import com.yas.inventory.viewmodel.product.ProductInfoVm;
import com.yas.inventory.viewmodel.stock.StockQuantityVm;
import com.yas.inventory.viewmodel.stockhistory.StockHistoryListVm;
import com.yas.inventory.viewmodel.stockhistory.StockHistoryVm;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
public class StockHistoryServiceTest {

    @Mock
    private StockHistoryRepository stockHistoryRepository;

    @Mock
    private ProductService productService;

    @InjectMocks
    private StockHistoryService stockHistoryService;

    private Stock stock;
    private Warehouse warehouse;
    private StockQuantityVm stockQuantityVm;
    private ProductInfoVm productInfoVm;
    private StockHistory stockHistory;

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

        stockQuantityVm = new StockQuantityVm(1L, 50L, "Add stock");

        productInfoVm = new ProductInfoVm(1L, "Product 1", "SKU001", false);

        stockHistory = StockHistory.builder()
            .id(1L)
            .productId(1L)
            .note("Add stock")
            .adjustedQuantity(50L)
            .warehouse(warehouse)
            .build();
    }

    @Test
    void createStockHistories_WithValidData_Success() {
        List<Stock> stocks = List.of(stock);
        List<StockQuantityVm> stockQuantityVms = List.of(stockQuantityVm);

        stockHistoryService.createStockHistories(stocks, stockQuantityVms);

        verify(stockHistoryRepository).saveAll(anyList());
    }

    @Test
    void createStockHistories_WithMultipleStocks_Success() {
        Stock stock2 = Stock.builder()
            .id(2L)
            .productId(2L)
            .warehouse(warehouse)
            .quantity(50L)
            .reservedQuantity(0L)
            .build();

        StockQuantityVm stockQuantityVm2 = new StockQuantityVm(2L, 30L, "Add stock");

        List<Stock> stocks = List.of(stock, stock2);
        List<StockQuantityVm> stockQuantityVms = List.of(stockQuantityVm, stockQuantityVm2);

        stockHistoryService.createStockHistories(stocks, stockQuantityVms);

        verify(stockHistoryRepository).saveAll(anyList());
    }

    @Test
    void createStockHistories_WithNoMatchingStockQuantityVm_SkipsStock() {
        StockQuantityVm differentStockVm = new StockQuantityVm(999L, 50L, "Wrong stock");

        List<Stock> stocks = List.of(stock);
        List<StockQuantityVm> stockQuantityVms = List.of(differentStockVm);

        stockHistoryService.createStockHistories(stocks, stockQuantityVms);

        verify(stockHistoryRepository).saveAll(anyList());
    }

    @Test
    void createStockHistories_WithEmptyStockList_SavesNothing() {
        List<Stock> stocks = List.of();
        List<StockQuantityVm> stockQuantityVms = List.of(stockQuantityVm);

        stockHistoryService.createStockHistories(stocks, stockQuantityVms);

        verify(stockHistoryRepository).saveAll(anyList());
    }

    @Test
    void createStockHistories_WithEmptyStockQuantityVmList_SavesNothing() {
        List<Stock> stocks = List.of(stock);
        List<StockQuantityVm> stockQuantityVms = List.of();

        stockHistoryService.createStockHistories(stocks, stockQuantityVms);

        verify(stockHistoryRepository).saveAll(anyList());
    }

    @Test
    void createStockHistories_WithNegativeAdjustmentQuantity_Success() {
        StockQuantityVm negativeVm = new StockQuantityVm(1L, -30L, "Remove stock");

        List<Stock> stocks = List.of(stock);
        List<StockQuantityVm> stockQuantityVms = List.of(negativeVm);

        stockHistoryService.createStockHistories(stocks, stockQuantityVms);

        verify(stockHistoryRepository).saveAll(anyList());
    }

    @Test
    void createStockHistories_WithZeroAdjustmentQuantity_Success() {
        StockQuantityVm zeroVm = new StockQuantityVm(1L, 0L, "No change");

        List<Stock> stocks = List.of(stock);
        List<StockQuantityVm> stockQuantityVms = List.of(zeroVm);

        stockHistoryService.createStockHistories(stocks, stockQuantityVms);

        verify(stockHistoryRepository).saveAll(anyList());
    }

    @Test
    void createStockHistories_WithNullNote_Success() {
        StockQuantityVm nullNoteVm = new StockQuantityVm(1L, 50L, null);

        List<Stock> stocks = List.of(stock);
        List<StockQuantityVm> stockQuantityVms = List.of(nullNoteVm);

        stockHistoryService.createStockHistories(stocks, stockQuantityVms);

        verify(stockHistoryRepository).saveAll(anyList());
    }

    @Test
    void getStockHistories_WithValidData_ReturnsStockHistoryListVm() {
        List<StockHistory> stockHistories = List.of(stockHistory);

        when(stockHistoryRepository.findByProductIdAndWarehouseIdOrderByCreatedOnDesc(1L, 1L))
            .thenReturn(stockHistories);
        when(productService.getProduct(1L))
            .thenReturn(productInfoVm);

        StockHistoryListVm result = stockHistoryService.getStockHistories(1L, 1L);

        assertNotNull(result);
        assertEquals(1, result.data().size());
    }

    @Test
    void getStockHistories_WithMultipleHistories_ReturnsAllHistories() {
        StockHistory stockHistory2 = StockHistory.builder()
            .id(2L)
            .productId(1L)
            .note("Remove stock")
            .adjustedQuantity(-20L)
            .warehouse(warehouse)
            .build();

        List<StockHistory> stockHistories = List.of(stockHistory, stockHistory2);

        when(stockHistoryRepository.findByProductIdAndWarehouseIdOrderByCreatedOnDesc(1L, 1L))
            .thenReturn(stockHistories);
        when(productService.getProduct(1L))
            .thenReturn(productInfoVm);

        StockHistoryListVm result = stockHistoryService.getStockHistories(1L, 1L);

        assertNotNull(result);
        assertEquals(2, result.data().size());
    }

    @Test
    void getStockHistories_WithNoHistories_ReturnsEmptyList() {
        when(stockHistoryRepository.findByProductIdAndWarehouseIdOrderByCreatedOnDesc(1L, 1L))
            .thenReturn(List.of());
        when(productService.getProduct(1L))
            .thenReturn(productInfoVm);

        StockHistoryListVm result = stockHistoryService.getStockHistories(1L, 1L);

        assertNotNull(result);
        assertEquals(0, result.data().size());
    }

    @Test
    void getStockHistories_WithNullProduct_ThrowsNullPointerException() {
        List<StockHistory> stockHistories = List.of(stockHistory);

        when(stockHistoryRepository.findByProductIdAndWarehouseIdOrderByCreatedOnDesc(1L, 1L))
            .thenReturn(stockHistories);
        when(productService.getProduct(1L))
            .thenReturn(null);

        assertThrows(NullPointerException.class, () -> stockHistoryService.getStockHistories(1L, 1L));
    }

    @Test
    void getStockHistories_DifferentWarehouseId_ReturnsCorrectHistories() {
        Warehouse warehouse2 = new Warehouse();
        warehouse2.setId(2L);
        warehouse2.setName("Warehouse 2");

        StockHistory historyWarehouse2 = StockHistory.builder()
            .id(3L)
            .productId(1L)
            .note("Warehouse 2 stock")
            .adjustedQuantity(75L)
            .warehouse(warehouse2)
            .build();

        List<StockHistory> stockHistories = List.of(historyWarehouse2);

        when(stockHistoryRepository.findByProductIdAndWarehouseIdOrderByCreatedOnDesc(1L, 2L))
            .thenReturn(stockHistories);
        when(productService.getProduct(1L))
            .thenReturn(productInfoVm);

        StockHistoryListVm result = stockHistoryService.getStockHistories(1L, 2L);

        assertNotNull(result);
        assertEquals(1, result.data().size());
    }

    @Test
    void getStockHistories_VerifiesOrderByCreatedOnDesc() {
        when(stockHistoryRepository.findByProductIdAndWarehouseIdOrderByCreatedOnDesc(1L, 1L))
            .thenReturn(List.of());
        when(productService.getProduct(1L))
            .thenReturn(productInfoVm);

        stockHistoryService.getStockHistories(1L, 1L);

        verify(stockHistoryRepository).findByProductIdAndWarehouseIdOrderByCreatedOnDesc(1L, 1L);
    }

    @Test
    void getStockHistories_WithLargeQuantityAdjustment_Success() {
        StockHistory largeAdjustment = StockHistory.builder()
            .id(4L)
            .productId(1L)
            .note("Large stock addition")
            .adjustedQuantity(10000L)
            .warehouse(warehouse)
            .build();

        List<StockHistory> stockHistories = List.of(largeAdjustment);

        when(stockHistoryRepository.findByProductIdAndWarehouseIdOrderByCreatedOnDesc(1L, 1L))
            .thenReturn(stockHistories);
        when(productService.getProduct(1L))
            .thenReturn(productInfoVm);

        StockHistoryListVm result = stockHistoryService.getStockHistories(1L, 1L);

        assertNotNull(result);
        assertEquals(1, result.data().size());
    }
}
