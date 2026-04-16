package com.yas.customer.service;

import com.yas.commonlibrary.exception.AccessDeniedException;
import com.yas.commonlibrary.exception.NotFoundException;
import com.yas.customer.model.UserAddress;
import com.yas.customer.repository.UserAddressRepository;
import com.yas.customer.viewmodel.address.ActiveAddressVm;
import com.yas.customer.viewmodel.address.AddressDetailVm;
import com.yas.customer.viewmodel.address.AddressPostVm;
import com.yas.customer.viewmodel.address.AddressVm;
import com.yas.customer.viewmodel.useraddress.UserAddressVm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserAddressServiceTest {

    @Mock private UserAddressRepository userAddressRepository;
    @Mock private LocationService locationService;

    @InjectMocks
    private UserAddressService userAddressService;

    private static final String USER_ID = "user-abc";

    @BeforeEach
    void mockAuthenticatedUser() {
        mockSecurityContext(USER_ID);
    }

    @Test
    void getUserAddressList_WhenAnonymous_ShouldThrowAccessDeniedException() {
        mockSecurityContext("anonymousUser");

        assertThatThrownBy(() -> userAddressService.getUserAddressList())
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getUserAddressList_WhenNoAddresses_ShouldReturnEmptyList() {
        when(userAddressRepository.findAllByUserId(USER_ID)).thenReturn(Collections.emptyList());
        when(locationService.getAddressesByIdList(Collections.emptyList())).thenReturn(Collections.emptyList());

        List<ActiveAddressVm> result = userAddressService.getUserAddressList();

        assertThat(result).isEmpty();
    }

    @Test
    void getUserAddressList_ShouldReturnMatchedAddressesSortedByIsActive() {
        UserAddress activeAddress = buildUserAddress(1L, true);
        UserAddress inactiveAddress = buildUserAddress(2L, false);
        when(userAddressRepository.findAllByUserId(USER_ID))
            .thenReturn(List.of(inactiveAddress, activeAddress));

        AddressDetailVm detail1 = buildAddressDetailVm(1L);
        AddressDetailVm detail2 = buildAddressDetailVm(2L);
        when(locationService.getAddressesByIdList(List.of(2L, 1L)))
            .thenReturn(List.of(detail1, detail2));

        List<ActiveAddressVm> result = userAddressService.getUserAddressList();

        // Active address should come first
        assertThat(result).hasSize(2);
        assertThat(result.get(0).isActive()).isTrue();
        assertThat(result.get(1).isActive()).isFalse();
    }
    @Test
    void getAddressDefault_WhenAnonymous_ShouldThrowAccessDeniedException() {
        mockSecurityContext("anonymousUser");

        assertThatThrownBy(() -> userAddressService.getAddressDefault())
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getAddressDefault_WhenNoActiveAddress_ShouldThrowNotFoundException() {
        when(userAddressRepository.findByUserIdAndIsActiveTrue(USER_ID))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> userAddressService.getAddressDefault())
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getAddressDefault_WhenActiveAddressExists_ShouldReturnAddressDetailVm() {
        UserAddress active = buildUserAddress(5L, true);
        when(userAddressRepository.findByUserIdAndIsActiveTrue(USER_ID))
            .thenReturn(Optional.of(active));

        AddressDetailVm expected = buildAddressDetailVm(5L);
        when(locationService.getAddressById(5L)).thenReturn(expected);

        AddressDetailVm result = userAddressService.getAddressDefault();

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(5L);
    }
    @Test
    void createAddress_WhenFirstAddress_ShouldSetIsActiveTrue() {
        when(userAddressRepository.findAllByUserId(USER_ID)).thenReturn(Collections.emptyList());

        AddressPostVm postVm = buildAddressPostVm();
        AddressVm addressVm = new AddressVm(99L);
        when(locationService.createAddress(postVm)).thenReturn(addressVm);

        UserAddress savedAddress = buildUserAddress(99L, true);
        when(userAddressRepository.save(any(UserAddress.class))).thenReturn(savedAddress);

        UserAddressVm result = userAddressService.createAddress(postVm);

        assertThat(result).isNotNull();
        verify(userAddressRepository).save(argThat(ua -> ua.getIsActive().equals(true)));
    }

    @Test
    void createAddress_WhenNotFirstAddress_ShouldSetIsActiveFalse() {
        UserAddress existing = buildUserAddress(1L, true);
        when(userAddressRepository.findAllByUserId(USER_ID)).thenReturn(List.of(existing));

        AddressPostVm postVm = buildAddressPostVm();
        AddressVm addressVm = new AddressVm(100L);
        when(locationService.createAddress(postVm)).thenReturn(addressVm);

        UserAddress savedAddress = buildUserAddress(100L, false);
        when(userAddressRepository.save(any(UserAddress.class))).thenReturn(savedAddress);

        UserAddressVm result = userAddressService.createAddress(postVm);

        assertThat(result).isNotNull();
        verify(userAddressRepository).save(argThat(ua -> ua.getIsActive().equals(false)));
    }
    @Test
    void deleteAddress_WhenAddressNotFound_ShouldThrowNotFoundException() {
        when(userAddressRepository.findOneByUserIdAndAddressId(USER_ID, 99L)).thenReturn(null);

        assertThatThrownBy(() -> userAddressService.deleteAddress(99L))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteAddress_WhenAddressExists_ShouldDeleteSuccessfully() {
        UserAddress userAddress = buildUserAddress(10L, false);
        when(userAddressRepository.findOneByUserIdAndAddressId(USER_ID, 10L)).thenReturn(userAddress);

        userAddressService.deleteAddress(10L);

        verify(userAddressRepository).delete(userAddress);
    }

    @Test
    void chooseDefaultAddress_ShouldSetOnlyTargetAddressActive() {
        UserAddress addr1 = buildUserAddress(1L, true);
        UserAddress addr2 = buildUserAddress(2L, false);
        when(userAddressRepository.findAllByUserId(USER_ID)).thenReturn(List.of(addr1, addr2));

        userAddressService.chooseDefaultAddress(2L);

        assertThat(addr1.getIsActive()).isFalse();
        assertThat(addr2.getIsActive()).isTrue();
        verify(userAddressRepository).saveAll(List.of(addr1, addr2));
    }

    @Test
    void chooseDefaultAddress_WhenNoAddresses_ShouldDoNothing() {
        when(userAddressRepository.findAllByUserId(USER_ID)).thenReturn(Collections.emptyList());

        userAddressService.chooseDefaultAddress(1L);

        verify(userAddressRepository).saveAll(Collections.emptyList());
    }
    private void mockSecurityContext(String name) {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn(name);
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
    }

    private UserAddress buildUserAddress(Long addressId, boolean isActive) {
        return UserAddress.builder()
            .userId(USER_ID)
            .addressId(addressId)
            .isActive(isActive)
            .build();
    }

    private AddressDetailVm buildAddressDetailVm(Long id) {
        return new AddressDetailVm(id, "John", "0123456789",
            "123 Main St", "Hanoi", "10000",
            1L, "Ba Dinh", 1L, "Hanoi", 84L, "Vietnam");
    }

    private AddressPostVm buildAddressPostVm() {
        return new AddressPostVm("Jane", "0987654321",
            "456 Le Loi", "HCMC", "70000",
            2L, 2L, 84L);
    }
}