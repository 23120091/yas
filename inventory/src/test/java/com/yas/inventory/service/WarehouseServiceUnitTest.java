package com.yas.inventory.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yas.commonlibrary.exception.DuplicatedException;
import com.yas.commonlibrary.exception.NotFoundException;
import com.yas.inventory.model.Warehouse;
import com.yas.inventory.model.enumeration.FilterExistInWhSelection;
import com.yas.inventory.repository.StockRepository;
import com.yas.inventory.repository.WarehouseRepository;
import com.yas.inventory.viewmodel.address.AddressDetailVm;
import com.yas.inventory.viewmodel.address.AddressPostVm;
import com.yas.inventory.viewmodel.address.AddressVm;
import com.yas.inventory.viewmodel.product.ProductInfoVm;
import com.yas.inventory.viewmodel.warehouse.WarehouseDetailVm;
import com.yas.inventory.viewmodel.warehouse.WarehouseGetVm;
import com.yas.inventory.viewmodel.warehouse.WarehouseListGetVm;
import com.yas.inventory.viewmodel.warehouse.WarehousePostVm;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
public class WarehouseServiceUnitTest {

    @Mock
    private WarehouseRepository warehouseRepository;
    @Mock
    private StockRepository stockRepository;
    @Mock
    private ProductService productService;
    @Mock
    private LocationService locationService;

    @InjectMocks
    private WarehouseService warehouseService;

    private Warehouse warehouse;
    private WarehousePostVm warehousePostVm;
    private AddressDetailVm addressDetailVm;
    private AddressVm addressVm;

    @BeforeEach
    void setUp() {
        warehouse = new Warehouse();
        warehouse.setId(1L);
        warehouse.setName("Warehouse 1");
        warehouse.setAddressId(10L);

        warehousePostVm = new WarehousePostVm(
            "1", "Warehouse 1", "Contact Name", "123456789", "Line 1", "Line 2",
            "City", "12345", 1L, 1L, 1L
        );

        addressDetailVm = new AddressDetailVm(
            10L, "Contact Name", "123456789", "Line 1", "Line 2",
            "City", "12345", 1L, "District", 1L, "State", 1L, "Country"
        );

        addressVm = new AddressVm(
            10L, "Contact Name", "123456789", "Line 1", "City", "12345", 1L, 1L, 1L
        );
    }

    @Test
    void findAllWarehouses_Success() {
        when(warehouseRepository.findAll()).thenReturn(List.of(warehouse));

        List<WarehouseGetVm> result = warehouseService.findAllWarehouses();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(warehouse.getId(), result.get(0).id());
        assertEquals(warehouse.getName(), result.get(0).name());
    }

    @Test
    void getProductWarehouse_Success() {
        ProductInfoVm productInfoVm = new ProductInfoVm(1L, "Product 1", "SKU1", false);
        when(stockRepository.getProductIdsInWarehouse(1L)).thenReturn(List.of(1L));
        when(productService.filterProducts("Product 1", "SKU1", List.of(1L), FilterExistInWhSelection.ALL))
            .thenReturn(List.of(productInfoVm));

        List<ProductInfoVm> result = warehouseService.getProductWarehouse(1L, "Product 1", "SKU1", FilterExistInWhSelection.ALL);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.get(0).existInWh());
    }

    @Test
    void findById_Exists_Success() {
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
        when(locationService.getAddressById(10L)).thenReturn(addressDetailVm);

        WarehouseDetailVm result = warehouseService.findById(1L);

        assertNotNull(result);
        assertEquals(warehouse.getId(), result.id());
        assertEquals(warehouse.getName(), result.name());
        assertEquals(addressDetailVm.addressLine1(), result.addressLine1());
    }

    @Test
    void findById_NotExists_ThrowsNotFoundException() {
        when(warehouseRepository.findById(1L)).thenReturn(Optional.empty());

        NotFoundException exception = assertThrows(NotFoundException.class, () -> warehouseService.findById(1L));
        assertEquals("The warehouse 1 is not found", exception.getMessage());
    }

    @Test
    void create_ValidData_Success() {
        when(warehouseRepository.existsByName(warehousePostVm.name())).thenReturn(false);
        when(locationService.createAddress(any(AddressPostVm.class))).thenReturn(addressVm);
        when(warehouseRepository.save(any(Warehouse.class))).thenReturn(warehouse);

        Warehouse result = warehouseService.create(warehousePostVm);

        assertNotNull(result);
        assertEquals(warehouse.getName(), result.getName());
        assertEquals(warehouse.getAddressId(), result.getAddressId());
    }

    @Test
    void create_DuplicateName_ThrowsDuplicatedException() {
        when(warehouseRepository.existsByName(warehousePostVm.name())).thenReturn(true);

        DuplicatedException exception = assertThrows(DuplicatedException.class, () -> warehouseService.create(warehousePostVm));
        assertEquals("Request name Warehouse 1 is already existed", exception.getMessage());
    }

    @Test
    void update_ValidData_Success() {
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
        when(warehouseRepository.existsByNameWithDifferentId(warehousePostVm.name(), 1L)).thenReturn(false);

        warehouseService.update(warehousePostVm, 1L);

        verify(locationService).updateAddress(anyLong(), any(AddressPostVm.class));
        verify(warehouseRepository).save(warehouse);
    }

    @Test
    void update_DuplicateName_ThrowsDuplicatedException() {
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
        when(warehouseRepository.existsByNameWithDifferentId(warehousePostVm.name(), 1L)).thenReturn(true);

        DuplicatedException exception = assertThrows(DuplicatedException.class, () -> warehouseService.update(warehousePostVm, 1L));
        assertEquals("Request name Warehouse 1 is already existed", exception.getMessage());
    }

    @Test
    void delete_WarehouseExists_Success() {
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));

        warehouseService.delete(1L);

        verify(warehouseRepository).deleteById(1L);
        verify(locationService).deleteAddress(warehouse.getAddressId());
    }

    @Test
    void getPageableWarehouses_Success() {
        Page<Warehouse> page = new PageImpl<>(List.of(warehouse), PageRequest.of(0, 10), 1);
        when(warehouseRepository.findAll(any(Pageable.class))).thenReturn(page);

        WarehouseListGetVm result = warehouseService.getPageableWarehouses(0, 10);

        assertNotNull(result);
        assertEquals(1, result.warehouseContent().size());
        assertEquals(0, result.pageNo());
        assertEquals(10, result.pageSize());
        assertEquals(1, result.totalElements());
        assertEquals(1, result.totalPages());
        assertTrue(result.isLast());
    }
}