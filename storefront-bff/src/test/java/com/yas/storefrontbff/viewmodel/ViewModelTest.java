package com.yas.storefrontbff.viewmodel;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ViewModelTest {

    @Test
    void tokenResponseVm_ShouldExposeAccessAndRefreshToken() {
        TokenResponseVm vm = new TokenResponseVm("access-token", "refresh-token");

        assertEquals("access-token", vm.accessToken());
        assertEquals("refresh-token", vm.refreshToken());
    }

    @Test
    void guestUserVm_ShouldExposeUserInformation() {
        GuestUserVm vm = new GuestUserVm("guest-id", "guest@example.com", "secret");

        assertEquals("guest-id", vm.userId());
        assertEquals("guest@example.com", vm.email());
        assertEquals("secret", vm.password());
    }

    @Test
    void cartDetailVm_ShouldExposeCartDetailFields() {
        CartDetailVm vm = new CartDetailVm(1L, 2L, 3);

        assertEquals(1L, vm.id());
        assertEquals(2L, vm.productId());
        assertEquals(3, vm.quantity());
    }

    @Test
    void cartGetDetailVm_ShouldExposeCartAndDetails() {
        CartDetailVm cartDetail = new CartDetailVm(10L, 20L, 2);
        CartGetDetailVm vm = new CartGetDetailVm(1L, "customer-1", List.of(cartDetail));

        assertEquals(1L, vm.id());
        assertEquals("customer-1", vm.customerId());
        assertEquals(List.of(cartDetail), vm.cartDetails());
    }

    @Test
    void cartItemVmFromCartDetailVm_ShouldMapProductIdAndQuantity() {
        CartDetailVm cartDetailVm = new CartDetailVm(11L, 22L, 4);

        CartItemVm vm = CartItemVm.fromCartDetailVm(cartDetailVm);

        assertEquals(22L, vm.productId());
        assertEquals(4, vm.quantity());
    }
}
