package com.yas.location.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yas.commonlibrary.exception.NotFoundException;
import com.yas.location.model.Address;
import com.yas.location.model.Country;
import com.yas.location.model.District;
import com.yas.location.model.StateOrProvince;
import com.yas.location.repository.AddressRepository;
import com.yas.location.repository.CountryRepository;
import com.yas.location.repository.DistrictRepository;
import com.yas.location.repository.StateOrProvinceRepository;
import com.yas.location.viewmodel.address.AddressDetailVm;
import com.yas.location.viewmodel.address.AddressGetVm;
import com.yas.location.viewmodel.address.AddressPostVm;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class AddressServiceUnitTest {

    @Mock
    private AddressRepository addressRepository;
    @Mock
    private CountryRepository countryRepository;
    @Mock
    private DistrictRepository districtRepository;
    @Mock
    private StateOrProvinceRepository stateOrProvinceRepository;

    @InjectMocks
    private AddressService addressService;

    private Address address;
    private Country country;
    private District district;
    private StateOrProvince stateOrProvince;
    private AddressPostVm addressPostVm;

    @BeforeEach
    void setUp() {
        country = Country.builder().name("Country").build();
        country.setId(1L);

        stateOrProvince = StateOrProvince.builder().name("State").country(country).build();
        stateOrProvince.setId(1L);

        district = District.builder().name("District").stateProvince(stateOrProvince).build();
        district.setId(1L);

        address = Address.builder()
            .contactName("John Doe")
            .city("City")
            .country(country)
            .district(district)
            .stateOrProvince(stateOrProvince)
            .build();
        address.setId(1L);

        addressPostVm = AddressPostVm.builder()
            .contactName("John Doe")
            .city("City")
            .countryId(1L)
            .districtId(1L)
            .stateOrProvinceId(1L)
            .build();
    }

    @Test
    void createAddress_ValidData_Success() {
        when(stateOrProvinceRepository.findById(1L)).thenReturn(Optional.of(stateOrProvince));
        when(countryRepository.findById(1L)).thenReturn(Optional.of(country));
        when(districtRepository.findById(1L)).thenReturn(Optional.of(district));
        when(addressRepository.save(any(Address.class))).thenReturn(address);

        AddressGetVm result = addressService.createAddress(addressPostVm);

        assertNotNull(result);
        assertEquals(address.getId(), result.id());
        verify(addressRepository).save(any(Address.class));
    }

    @Test
    void createAddress_InvalidCountry_ThrowsNotFoundException() {
        when(stateOrProvinceRepository.findById(1L)).thenReturn(Optional.of(stateOrProvince));
        when(countryRepository.findById(1L)).thenReturn(Optional.empty());

        NotFoundException exception = assertThrows(NotFoundException.class, 
            () -> addressService.createAddress(addressPostVm));
        assertEquals("The country 1 is not found", exception.getMessage());
    }

    @Test
    void getAddress_Exists_Success() {
        when(addressRepository.findById(1L)).thenReturn(Optional.of(address));

        AddressDetailVm result = addressService.getAddress(1L);

        assertNotNull(result);
        assertEquals(address.getId(), result.id());
        assertEquals(address.getContactName(), result.contactName());
    }

    @Test
    void getAddress_NotExists_ThrowsNotFoundException() {
        when(addressRepository.findById(1L)).thenReturn(Optional.empty());

        NotFoundException exception = assertThrows(NotFoundException.class, 
            () -> addressService.getAddress(1L));
        assertEquals("The address 1 is not found", exception.getMessage());
    }

    @Test
    void updateAddress_Exists_Success() {
        when(addressRepository.findById(1L)).thenReturn(Optional.of(address));
        when(stateOrProvinceRepository.findById(1L)).thenReturn(Optional.of(stateOrProvince));
        when(countryRepository.findById(1L)).thenReturn(Optional.of(country));
        when(districtRepository.findById(1L)).thenReturn(Optional.of(district));

        addressService.updateAddress(1L, addressPostVm);

        verify(addressRepository).save(address);
    }

    @Test
    void deleteAddress_Exists_Success() {
        when(addressRepository.findById(1L)).thenReturn(Optional.of(address));

        addressService.deleteAddress(1L);

        verify(addressRepository).delete(address);
    }

    @Test
    void getAddressList_Success() {
        when(addressRepository.findAllByIdIn(List.of(1L))).thenReturn(List.of(address));

        List<AddressDetailVm> result = addressService.getAddressList(List.of(1L));

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(address.getId(), result.get(0).id());
    }
}