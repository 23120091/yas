package com.yas.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yas.payment.service.PaymentService;
import com.yas.payment.viewmodel.CapturePaymentRequestVm;
import com.yas.payment.viewmodel.CapturePaymentResponseVm;
import com.yas.payment.viewmodel.InitPaymentRequestVm;
import com.yas.payment.viewmodel.InitPaymentResponseVm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
public class PaymentControllerTest {

    private MockMvc mockMvc;

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private PaymentController paymentController;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(paymentController).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void testInitPayment() throws Exception {
        InitPaymentRequestVm requestVm = InitPaymentRequestVm.builder().build();
        
        InitPaymentResponseVm responseVm = InitPaymentResponseVm.builder()
            .status("CREATED")
            .redirectUrl("http://test-url")
            .paymentId("123")
            .build();
        
        when(paymentService.initPayment(any(InitPaymentRequestVm.class))).thenReturn(responseVm);

        mockMvc.perform(post("/init")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestVm)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.redirectUrl").value("http://test-url"));
    }

    @Test
    void testCapturePayment() throws Exception {
        CapturePaymentRequestVm requestVm = CapturePaymentRequestVm.builder().build();
        CapturePaymentResponseVm responseVm = CapturePaymentResponseVm.builder()
            .paymentStatus(com.yas.payment.model.enumeration.PaymentStatus.COMPLETED)
            .failureMessage("test-msg")
            .build();

        when(paymentService.capturePayment(any(CapturePaymentRequestVm.class))).thenReturn(responseVm);

        mockMvc.perform(post("/capture")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestVm)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.failureMessage").value("test-msg"));
    }

    @Test
    void testCancelPayment() throws Exception {
        mockMvc.perform(get("/cancel"))
                .andExpect(status().isOk())
                .andExpect(content().string("Payment cancelled"));
    }
}

