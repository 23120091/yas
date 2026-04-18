package com.yas.order.service;

import com.yas.commonlibrary.exception.NotFoundException;
import com.yas.commonlibrary.utils.AuthenticationUtils;
import com.yas.order.mapper.OrderMapper;
import com.yas.order.model.Order;
import com.yas.order.model.OrderItem;
import com.yas.order.model.csv.OrderItemCsv;
import com.yas.order.model.enumeration.DeliveryMethod;
import com.yas.order.model.enumeration.DeliveryStatus;
import com.yas.order.model.enumeration.OrderStatus;
import com.yas.order.model.enumeration.PaymentStatus;
import com.yas.order.model.request.OrderRequest;
import com.yas.order.repository.OrderItemRepository;
import com.yas.order.repository.OrderRepository;
import com.yas.order.viewmodel.order.*;
import com.yas.order.viewmodel.orderaddress.OrderAddressPostVm;
import com.yas.order.viewmodel.product.ProductVariationVm;
import com.yas.order.viewmodel.promotion.PromotionUsageVm;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.util.Pair;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private ProductService productService;
    @Mock private CartService cartService;
    @Mock private OrderMapper orderMapper;
    @Mock private PromotionService promotionService;

    @InjectMocks
    private OrderService orderService;

    private MockedStatic<AuthenticationUtils> authUtilsMock;

    private static final String USER_ID = "user-001";
    private static final Long ORDER_ID = 1L;

    @BeforeEach
    void setUp() {
        authUtilsMock = mockStatic(AuthenticationUtils.class);
    }

    @AfterEach
    void tearDown() {
        authUtilsMock.close();
    }

    // =========================================================================
    // createOrder
    // =========================================================================

    @Nested
    class CreateOrderTest {

        /**
         * Happy path: order và items được lưu, các downstream service được gọi,
         * trả về OrderVm không null.
         */
        @Test
        void createOrder_ShouldSaveOrderAndItems_AndCallDownstreamServices() {
            OrderPostVm postVm = buildOrderPostVm();
            Order savedOrder = buildOrder(ORDER_ID, OrderStatus.PENDING);

            when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
            // acceptOrder() được gọi nội bộ trong createOrder → cần stub findById
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(savedOrder));

            OrderVm result = orderService.createOrder(postVm);

            assertThat(result).isNotNull();
            verify(orderRepository, atLeastOnce()).save(any(Order.class));
            verify(orderItemRepository).saveAll(anySet());
            verify(productService).subtractProductStockQuantity(any(OrderVm.class));
            verify(cartService).deleteCartItems(any(OrderVm.class));
            verify(promotionService).updateUsagePromotion(anyList());
        }

        /**
         * Order được tạo với orderStatus = PENDING và deliveryStatus = PREPARING.
         */
        @Test
        void createOrder_ShouldSetOrderStatusPendingAndDeliveryStatusPreparing() {
            OrderPostVm postVm = buildOrderPostVm();
            Order savedOrder = buildOrder(ORDER_ID, OrderStatus.PENDING);

            when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(savedOrder));

            orderService.createOrder(postVm);

            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository, atLeastOnce()).save(captor.capture());
            Order captured = captor.getAllValues().get(0);
            assertThat(captured.getOrderStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(captured.getDeliveryStatus()).isEqualTo(DeliveryStatus.PREPARING);
        }

        /**
         * Mỗi OrderItem tạo ra một PromotionUsageVm → postVm có 2 items → size = 2.
         */
        @Test
        void createOrder_ShouldCreateOnePromotionUsageVmPerOrderItem() {
            OrderPostVm postVm = buildOrderPostVm();
            Order savedOrder = buildOrder(ORDER_ID, OrderStatus.PENDING);

            when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(savedOrder));

            orderService.createOrder(postVm);

            ArgumentCaptor<List<PromotionUsageVm>> captor = ArgumentCaptor.forClass(List.class);
            verify(promotionService).updateUsagePromotion(captor.capture());
            assertThat(captor.getValue()).hasSize(2);
        }

        /**
         * acceptOrder() được gọi nội bộ; nếu order không tìm được thì ném NotFoundException.
         */
        @Test
        void createOrder_WhenAcceptOrderCannotFindOrder_ShouldThrowNotFoundException() {
            OrderPostVm postVm = buildOrderPostVm();
            Order savedOrder = buildOrder(ORDER_ID, OrderStatus.PENDING);

            when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.createOrder(postVm))
                .isInstanceOf(NotFoundException.class);
        }
    }

    // =========================================================================
    // getOrderWithItemsById
    // =========================================================================

    @Nested
    class GetOrderWithItemsByIdTest {

        @Test
        void getOrderWithItemsById_WhenOrderExists_ShouldReturnOrderVm() {
            Order order = buildOrder(ORDER_ID, OrderStatus.PENDING);
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
            when(orderItemRepository.findAllByOrderId(ORDER_ID))
                .thenReturn(List.of(buildOrderItem(ORDER_ID)));

            OrderVm result = orderService.getOrderWithItemsById(ORDER_ID);

            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(ORDER_ID);
        }

        @Test
        void getOrderWithItemsById_WhenOrderNotFound_ShouldThrowNotFoundException() {
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.getOrderWithItemsById(ORDER_ID))
                .isInstanceOf(NotFoundException.class);
        }
    }

    // =========================================================================
    // getAllOrder
    // =========================================================================

    @Nested
    class GetAllOrderTest {

        @Test
        @SuppressWarnings("unchecked")
        void getAllOrder_WhenOrdersExist_ShouldReturnPopulatedOrderListVm() {
            Order order = buildOrder(ORDER_ID, OrderStatus.PENDING);
            Page<Order> page = new PageImpl<>(List.of(order));
            when(orderRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

            OrderListVm result = orderService.getAllOrder(
                Pair.of(ZonedDateTime.now().minusDays(1), ZonedDateTime.now()),
                null,
                List.of(),
                Pair.of("Vietnam", "0123456789"),
                "test@example.com",
                Pair.of(0, 10)
            );

            assertThat(result.orderList()).hasSize(1);
            assertThat(result.totalElements()).isEqualTo(1L);
        }

        @Test
        @SuppressWarnings("unchecked")
        void getAllOrder_WhenPageIsEmpty_ShouldReturnNullOrderListAndZeroCounts() {
            when(orderRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(Page.empty());

            OrderListVm result = orderService.getAllOrder(
                Pair.of(ZonedDateTime.now().minusDays(1), ZonedDateTime.now()),
                null,
                List.of(OrderStatus.PENDING),
                Pair.of(null, null),
                null,
                Pair.of(0, 10)
            );

            assertThat(result.orderList()).isNull();
            assertThat(result.totalElements()).isZero();
            assertThat(result.totalPages()).isZero();
        }

        /**
         * Khi orderStatus truyền vào empty → logic dùng tất cả statuses, không throw.
         */
        @Test
        @SuppressWarnings("unchecked")
        void getAllOrder_WhenOrderStatusIsEmpty_ShouldFallbackToAllStatuses() {
            when(orderRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(Page.empty());

            orderService.getAllOrder(
                Pair.of(ZonedDateTime.now().minusDays(7), ZonedDateTime.now()),
                "product",
                List.of(),
                Pair.of("VN", "0000"),
                "a@b.com",
                Pair.of(0, 5)
            );

            verify(orderRepository).findAll(any(Specification.class), any(Pageable.class));
        }
    }

    // =========================================================================
    // getLatestOrders
    // =========================================================================

    @Nested
    class GetLatestOrdersTest {

        @Test
        void getLatestOrders_WhenCountIsZero_ShouldReturnEmptyListWithoutQueryingDb() {
            List<OrderBriefVm> result = orderService.getLatestOrders(0);

            assertThat(result).isEmpty();
            verifyNoInteractions(orderRepository);
        }

        @Test
        void getLatestOrders_WhenCountIsNegative_ShouldReturnEmptyListWithoutQueryingDb() {
            List<OrderBriefVm> result = orderService.getLatestOrders(-1);

            assertThat(result).isEmpty();
            verifyNoInteractions(orderRepository);
        }

        @Test
        void getLatestOrders_WhenDbReturnsEmpty_ShouldReturnEmptyList() {
            when(orderRepository.getLatestOrders(any(Pageable.class)))
                .thenReturn(Collections.emptyList());

            assertThat(orderService.getLatestOrders(5)).isEmpty();
        }

        @Test
        void getLatestOrders_WhenDbReturnsOrders_ShouldReturnMappedList() {
            when(orderRepository.getLatestOrders(any(Pageable.class)))
                .thenReturn(List.of(
                    buildOrder(1L, OrderStatus.PENDING),
                    buildOrder(2L, OrderStatus.ACCEPTED)
                ));

            assertThat(orderService.getLatestOrders(2)).hasSize(2);
        }
    }

    // =========================================================================
    // isOrderCompletedWithUserIdAndProductId
    // =========================================================================

    @Nested
    class IsOrderCompletedWithUserIdAndProductIdTest {

        @Test
        @SuppressWarnings("unchecked")
        void whenNoProductVariations_ShouldUseOriginalProductId_AndReturnTrue() {
            authUtilsMock.when(AuthenticationUtils::extractUserId).thenReturn(USER_ID);
            when(productService.getProductVariations(10L)).thenReturn(Collections.emptyList());
            when(orderRepository.findOne(any(Specification.class)))
                .thenReturn(Optional.of(buildOrder(1L, OrderStatus.COMPLETED)));

            OrderExistsByProductAndUserGetVm result =
                orderService.isOrderCompletedWithUserIdAndProductId(10L);

            assertThat(result.isPresent()).isTrue();
        }

        @Test
        @SuppressWarnings("unchecked")
        void whenProductVariationsExist_ShouldUseVariationIds_AndReturnFalse() {
            authUtilsMock.when(AuthenticationUtils::extractUserId).thenReturn(USER_ID);
            when(productService.getProductVariations(10L))
                .thenReturn(List.of(
                    new ProductVariationVm(11L, "Red", "SKU-1"),
                    new ProductVariationVm(12L, "Blue", "SKU-2")
                ));
            when(orderRepository.findOne(any(Specification.class))).thenReturn(Optional.empty());

            OrderExistsByProductAndUserGetVm result =
                orderService.isOrderCompletedWithUserIdAndProductId(10L);

            assertThat(result.isPresent()).isFalse();
        }

        @Test
        @SuppressWarnings("unchecked")
        void whenProductVariationsIsNull_ShouldUseOriginalProductId() {
            authUtilsMock.when(AuthenticationUtils::extractUserId).thenReturn(USER_ID);
            when(productService.getProductVariations(10L)).thenReturn(null);
            when(orderRepository.findOne(any(Specification.class))).thenReturn(Optional.empty());

            OrderExistsByProductAndUserGetVm result =
                orderService.isOrderCompletedWithUserIdAndProductId(10L);

            assertThat(result.isPresent()).isFalse();
        }
    }

    // =========================================================================
    // getMyOrders
    // =========================================================================

    @Nested
    class GetMyOrdersTest {

        @Test
        @SuppressWarnings("unchecked")
        void getMyOrders_ShouldReturnListOfOrderGetVm() {
            authUtilsMock.when(AuthenticationUtils::extractUserId).thenReturn(USER_ID);
            when(orderRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(buildOrder(ORDER_ID, OrderStatus.PENDING)));

            List<OrderGetVm> result = orderService.getMyOrders("product", OrderStatus.PENDING);

            assertThat(result).hasSize(1);
        }

        @Test
        @SuppressWarnings("unchecked")
        void getMyOrders_WhenNoOrders_ShouldReturnEmptyList() {
            authUtilsMock.when(AuthenticationUtils::extractUserId).thenReturn(USER_ID);
            when(orderRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(Collections.emptyList());

            assertThat(orderService.getMyOrders(null, null)).isEmpty();
        }
    }

    // =========================================================================
    // findOrderVmByCheckoutId
    // =========================================================================

    @Nested
    class FindOrderVmByCheckoutIdTest {

        @Test
        void findOrderVmByCheckoutId_WhenFound_ShouldReturnOrderGetVmWithItems() {
            Order order = buildOrder(ORDER_ID, OrderStatus.PENDING);
            when(orderRepository.findByCheckoutId("checkout-abc")).thenReturn(Optional.of(order));
            when(orderItemRepository.findAllByOrderId(ORDER_ID))
                .thenReturn(List.of(buildOrderItem(ORDER_ID)));

            OrderGetVm result = orderService.findOrderVmByCheckoutId("checkout-abc");

            assertThat(result).isNotNull();
        }

        @Test
        void findOrderVmByCheckoutId_WhenCheckoutNotFound_ShouldThrowNotFoundException() {
            when(orderRepository.findByCheckoutId("invalid")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.findOrderVmByCheckoutId("invalid"))
                .isInstanceOf(NotFoundException.class);
        }
    }

    // =========================================================================
    // findOrderByCheckoutId
    // =========================================================================

    @Nested
    class FindOrderByCheckoutIdTest {

        @Test
        void findOrderByCheckoutId_WhenFound_ShouldReturnOrder() {
            Order order = buildOrder(ORDER_ID, OrderStatus.PENDING);
            when(orderRepository.findByCheckoutId("checkout-xyz")).thenReturn(Optional.of(order));

            Order result = orderService.findOrderByCheckoutId("checkout-xyz");

            assertThat(result.getId()).isEqualTo(ORDER_ID);
        }

        @Test
        void findOrderByCheckoutId_WhenNotFound_ShouldThrowNotFoundException() {
            when(orderRepository.findByCheckoutId("missing")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.findOrderByCheckoutId("missing"))
                .isInstanceOf(NotFoundException.class);
        }
    }

    // =========================================================================
    // updateOrderPaymentStatus
    // =========================================================================

    @Nested
    class UpdateOrderPaymentStatusTest {

        @Test
        void updateOrderPaymentStatus_WhenPaymentCompleted_ShouldSetOrderStatusPaid() {
            Order order = buildOrder(ORDER_ID, OrderStatus.PENDING);
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
            when(orderRepository.save(order)).thenReturn(order);

            PaymentOrderStatusVm input = PaymentOrderStatusVm.builder()
                .orderId(ORDER_ID)
                .paymentId(1L)
                .paymentStatus(PaymentStatus.COMPLETED.name())
                .build();

            PaymentOrderStatusVm result = orderService.updateOrderPaymentStatus(input);

            assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.PAID);
            assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.COMPLETED);
            assertThat(result.paymentId()).isEqualTo(1L);
            assertThat(result.orderId()).isEqualTo(ORDER_ID);
        }

        @Test
        void updateOrderPaymentStatus_WhenPaymentNotCompleted_ShouldNotChangeOrderStatus() {
            Order order = buildOrder(ORDER_ID, OrderStatus.PENDING);
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
            when(orderRepository.save(order)).thenReturn(order);

            PaymentOrderStatusVm input = PaymentOrderStatusVm.builder()
                .orderId(ORDER_ID)
                .paymentId(2L)
                .paymentStatus(PaymentStatus.PENDING.name())
                .build();

            orderService.updateOrderPaymentStatus(input);

            assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        }

        @Test
        void updateOrderPaymentStatus_WhenOrderNotFound_ShouldThrowNotFoundException() {
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

            PaymentOrderStatusVm input = PaymentOrderStatusVm.builder()
                .orderId(ORDER_ID)
                .paymentId(3L)
                .paymentStatus(PaymentStatus.COMPLETED.name())
                .build();

            assertThatThrownBy(() -> orderService.updateOrderPaymentStatus(input))
                .isInstanceOf(NotFoundException.class);
        }
    }

    // =========================================================================
    // rejectOrder
    // =========================================================================

    @Nested
    class RejectOrderTest {

        @Test
        void rejectOrder_WhenOrderExists_ShouldSetStatusRejectAndRejectReason() {
            Order order = buildOrder(ORDER_ID, OrderStatus.PENDING);
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

            orderService.rejectOrder(ORDER_ID, "Out of stock");

            assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.REJECT);
            assertThat(order.getRejectReason()).isEqualTo("Out of stock");
            verify(orderRepository).save(order);
        }

        @Test
        void rejectOrder_WhenOrderNotFound_ShouldThrowNotFoundException() {
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.rejectOrder(ORDER_ID, "reason"))
                .isInstanceOf(NotFoundException.class);
        }
    }

    // =========================================================================
    // acceptOrder
    // =========================================================================

    @Nested
    class AcceptOrderTest {

        @Test
        void acceptOrder_WhenOrderExists_ShouldSetStatusAccepted() {
            Order order = buildOrder(ORDER_ID, OrderStatus.PENDING);
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

            orderService.acceptOrder(ORDER_ID);

            assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.ACCEPTED);
            verify(orderRepository).save(order);
        }

        @Test
        void acceptOrder_WhenOrderNotFound_ShouldThrowNotFoundException() {
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.acceptOrder(ORDER_ID))
                .isInstanceOf(NotFoundException.class);
        }
    }

    // =========================================================================
    // exportCsv
    // =========================================================================

    @Nested
    class ExportCsvTest {

        /**
         * Khi không có order → orderList = null → trả về CSV rỗng, mapper không được gọi.
         */
        @Test
        @SuppressWarnings("unchecked")
        void exportCsv_WhenNoOrders_ShouldReturnEmptyCsvBytesWithoutCallingMapper() throws IOException {
            when(orderRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(Page.empty());

            byte[] result = orderService.exportCsv(buildOrderRequest());

            assertThat(result).isNotNull();
            verifyNoInteractions(orderMapper);
        }

        /**
         * Khi có orders → mapper được gọi cho mỗi OrderBriefVm và bytes được trả về.
         */
        @Test
        @SuppressWarnings("unchecked")
        void exportCsv_WhenOrdersExist_ShouldCallMapperAndReturnBytes() throws IOException {
            Page<Order> page = new PageImpl<>(List.of(buildOrder(ORDER_ID, OrderStatus.PENDING)));
            when(orderRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
            when(orderMapper.toCsv(any(OrderBriefVm.class))).thenReturn(OrderItemCsv.builder().build());

            byte[] result = orderService.exportCsv(buildOrderRequest());

            assertThat(result).isNotNull();
            verify(orderMapper).toCsv(any(OrderBriefVm.class));
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private Order buildOrder(Long id, OrderStatus status) {
        Order order = new Order();
        order.setId(id);
        order.setOrderStatus(status);
        order.setDeliveryStatus(DeliveryStatus.PREPARING);
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.setCouponCode("COUPON10");
        return order;
    }

    private OrderItem buildOrderItem(Long orderId) {
        OrderItem item = new OrderItem();
        item.setProductId(100L);
        item.setProductName("Test Product");
        item.setQuantity(1);
        item.setOrderId(orderId);
        return item;
    }

    private OrderAddressPostVm buildAddressPostVm() {
        return new OrderAddressPostVm(
            "John Doe", "0123456789",
            "123 Main St", null, "Hanoi", "10000",
            1L, "Ba Dinh", 1L, "Hanoi", 84L, "Vietnam"
        );
    }

    private OrderPostVm buildOrderPostVm() {
        List<OrderItemPostVm> items = List.of(
            new OrderItemPostVm(1L, "Product A", 2, BigDecimal.valueOf(50000),
                "note1", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
            new OrderItemPostVm(2L, "Product B", 1, BigDecimal.valueOf(30000),
                "note2", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
        );
        return new OrderPostVm(
            "test@example.com", "Note",
            buildAddressPostVm(), buildAddressPostVm(),
            "COUPON10", 0.0f, 10.0f, 2,
            BigDecimal.valueOf(80000), BigDecimal.valueOf(5000),
            "checkout-001",
            DeliveryMethod.GRAB_EXPRESS,
            null,
            PaymentStatus.PENDING,
            items
        );
    }

    private OrderRequest buildOrderRequest() {
        OrderRequest request = new OrderRequest();
        request.setCreatedFrom(ZonedDateTime.now().minusDays(7));
        request.setCreatedTo(ZonedDateTime.now());
        request.setProductName(null);
        request.setOrderStatus(List.of());
        request.setBillingCountry(null);
        request.setBillingPhoneNumber(null);
        request.setEmail(null);
        request.setPageNo(0);
        request.setPageSize(10);
        return request;
    }
}