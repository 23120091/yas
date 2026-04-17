package com.yas.payment.service.provider.handler;

import com.yas.payment.model.CapturedPayment;
import com.yas.payment.model.InitiatedPayment;
import com.yas.payment.model.enumeration.PaymentMethod;
import com.yas.payment.model.enumeration.PaymentStatus;
import com.yas.payment.paypal.service.PaypalService;
import com.yas.payment.paypal.viewmodel.PaypalCapturePaymentRequest;
import com.yas.payment.paypal.viewmodel.PaypalCapturePaymentResponse;
import com.yas.payment.paypal.viewmodel.PaypalCreatePaymentRequest;
import com.yas.payment.paypal.viewmodel.PaypalCreatePaymentResponse;
import com.yas.payment.service.PaymentProviderService;
import com.yas.payment.viewmodel.CapturePaymentRequestVm;
import com.yas.payment.viewmodel.InitPaymentRequestVm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PaypalHandlerTest {

    @Mock
    private PaymentProviderService paymentProviderService;

    @Mock
    private PaypalService paypalService;

    @InjectMocks
    private PaypalHandler paypalHandler;

    @Test
    void testGetProviderId() {
        assertEquals(PaymentMethod.PAYPAL.name(), paypalHandler.getProviderId());
    }

    @Test
    void testInitPayment() {
        InitPaymentRequestVm requestVm = new InitPaymentRequestVm();
        
        PaypalCreatePaymentResponse response = new PaypalCreatePaymentResponse();
        response.status("CREATED");
        response.paymentId("pay123");
        response.redirectUrl("http://paypal.com");

        when(paymentProviderService.getPaymentSettings(anyString())).thenReturn("{}");
        when(paypalService.createPayment(any(PaypalCreatePaymentRequest.class))).thenReturn(response);

        InitiatedPayment result = paypalHandler.initPayment(requestVm);

        assertNotNull(result);
        assertEquals("CREATED", result.getStatus());
        assertEquals("pay123", result.getPaymentId());
        assertEquals("http://paypal.com", result.getRedirectUrl());
    }

    @Test
    void testCapturePayment() {
        CapturePaymentRequestVm requestVm = new CapturePaymentRequestVm();

        PaypalCapturePaymentResponse response = new PaypalCapturePaymentResponse();
        response.checkoutId("chk123");
        response.amount(BigDecimal.TEN);
        response.paymentFee(BigDecimal.ONE);
        response.gatewayTransactionId("gw123");
        response.paymentMethod(PaymentMethod.PAYPAL.name());
        response.paymentStatus(PaymentStatus.COMPLETED.name());
        response.failureMessage("");

        when(paymentProviderService.getPaymentSettings(anyString())).thenReturn("{}");
        when(paypalService.capturePayment(any(PaypalCapturePaymentRequest.class))).thenReturn(response);

        CapturedPayment result = paypalHandler.capturePayment(requestVm);

        assertNotNull(result);
        assertEquals("chk123", result.getCheckoutId());
        assertEquals(PaymentMethod.PAYPAL, result.getPaymentMethod());
        assertEquals(PaymentStatus.COMPLETED, result.getPaymentStatus());
    }
}
