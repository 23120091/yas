package com.yas.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yas.payment.service.PaymentProviderService;
import com.yas.payment.viewmodel.paymentprovider.CreatePaymentVm;
import com.yas.payment.viewmodel.paymentprovider.PaymentProviderVm;
import com.yas.payment.viewmodel.paymentprovider.UpdatePaymentVm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
public class PaymentProviderControllerTest {

    private MockMvc mockMvc;

    @Mock
    private PaymentProviderService paymentProviderService;

    @InjectMocks
    private PaymentProviderController paymentProviderController;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(paymentProviderController)
            .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
            .build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void testCreatePaymentProvider() throws Exception {
        CreatePaymentVm createVm = new CreatePaymentVm();
        createVm.setId("1");
        createVm.setName("Paypal");
        createVm.setConfigureUrl("https://example.com/paypal");
        PaymentProviderVm providerVm = new PaymentProviderVm("1", "Paypal", "url", 1, 1L, "icon");

        when(paymentProviderService.create(any(CreatePaymentVm.class))).thenReturn(providerVm);

        mockMvc.perform(post("/backoffice/payment-providers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createVm)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("1"))
                .andExpect(jsonPath("$.name").value("Paypal"));
    }

    @Test
    void testUpdatePaymentProvider() throws Exception {
        UpdatePaymentVm updateVm = new UpdatePaymentVm();
        updateVm.setId("1");
        updateVm.setName("Stripe");
        updateVm.setConfigureUrl("https://example.com/stripe");
        PaymentProviderVm providerVm = new PaymentProviderVm("1", "Stripe", "url", 1, 1L, "icon");

        when(paymentProviderService.update(any(UpdatePaymentVm.class))).thenReturn(providerVm);

        mockMvc.perform(put("/backoffice/payment-providers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateVm)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("1"))
                .andExpect(jsonPath("$.name").value("Stripe"));
    }

    @Test
    void testGetAllPaymentProviders() throws Exception {
        PaymentProviderVm providerVm = new PaymentProviderVm("1", "Paypal", "url", 1, 1L, "icon");

        when(paymentProviderService.getEnabledPaymentProviders(any(Pageable.class))).thenReturn(List.of(providerVm));

        mockMvc.perform(get("/storefront/payment-providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("1"))
                .andExpect(jsonPath("$[0].name").value("Paypal"));
    }
}
