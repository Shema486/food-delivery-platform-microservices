package order_service.service;


import lombok.AllArgsConstructor;
import order_service.config.RabbitMQConfig;
import order_service.dto.OrderItemRequest;
import order_service.dto.OrderResponse;
import order_service.dto.PlaceOrderRequest;
import order_service.dto.events.OrderCancelledEvent;
import order_service.dto.events.OrderPlacedEvent;
import order_service.dto.external.CustomerResponse;
import order_service.dto.external.MenuItemResponse;
import order_service.dto.external.RestaurantResponse;
import order_service.entity.Order;
import order_service.entity.OrderItem;
import order_service.exception.ResourceNotFoundException;
import order_service.exception.UnauthorizedException;
import order_service.feign.CustomerInterface;
import order_service.feign.RestaurantInterface;
import order_service.repository.OrderRepository;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * MONOLITH COUPLING — THIS IS THE WORST OFFENDER:
 * <p>
 * OrderService directly depends on:
 * - CustomerService  (to get customer entity)
 * - RestaurantService (to get restaurant and menu item entities)
 * - DeliveryService  (to create delivery SYNCHRONOUSLY)
 * <p>
 * In microservices:
 * 1. Store customerId / restaurantId as Long values
 * 2. Validate via Feign calls to Customer Service / Restaurant Service
 * 3. Publish OrderPlacedEvent — Delivery Service subscribes asynchronously
 */
@Service
@AllArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final CustomerInterface customerService;       // CROSS-DOMAIN
    private final RestaurantInterface restaurantService;  // CROSS-DOMAIN
    private final RabbitTemplate rabbitTemplate; // For publishing events to RabbitMQ

    @Transactional
    public OrderResponse placeOrder(String customerUsername, PlaceOrderRequest request) {

        // These Feign calls are fine — you need this data to build the order
        CustomerResponse customer = customerService.getName(customerUsername);
        RestaurantResponse restaurant = restaurantService.getRestaurantById(request.getRestaurantId());

        if (!restaurant.isActive()) {
            throw new IllegalStateException("Restaurant is currently not accepting orders");
        }

        // Build and save order — same as before
        Order order = Order.builder()
                .customerId(customer.getId())
                .customerName(customer.getFirstName() + " " + customer.getLastName())       // MONOLITH: direct entity reference
                .restaurantId(restaurant.getId())
                .restaurantName(restaurant.getName())
                .restaurantAddress(restaurant.getAddress())
                .deliveryAddress(request.getDeliveryAddress() != null ? request.getDeliveryAddress() : customer.getDeliveryAddress())
                .specialInstructions(request.getSpecialInstructions())
                .estimatedDeliveryTime(LocalDateTime.now().plusMinutes(restaurant.getEstimatedDeliveryMinutes()))
                .build();

        // ... build order items, calculate total (same as your existing code) ...
        // MONOLITH: directly fetching MenuItem entities from Restaurant domain
        BigDecimal total = BigDecimal.ZERO;
        for (OrderItemRequest itemReq : request.getItems()) {
            MenuItemResponse menuItem = restaurantService.getMenuItem(itemReq.getMenuItemId());

            if (!menuItem.isAvailable()) {
                throw new IllegalStateException("Menu item '" + menuItem.getName() + "' is not available");
            }
            if (!menuItem.getRestaurantId().equals(restaurant.getId())) {
                throw new IllegalStateException("Menu item '" + menuItem.getName()
                        + "' does not belong to restaurant '" + restaurant.getName() + "'");
            }

            BigDecimal subtotal = menuItem.getPrice().multiply(BigDecimal.valueOf(itemReq.getQuantity()));

            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .menuItemId(menuItem.getId())  // MONOLITH: cross-domain entity reference
                    .itemName(menuItem.getName())
                    .quantity(itemReq.getQuantity())
                    .unitPrice(menuItem.getPrice())
                    .subtotal(subtotal)
                    .specialInstructions(itemReq.getSpecialInstructions())
                    .build();

            order.getItems().add(orderItem);
            total = total.add(subtotal);
        }

        order.setTotalAmount(total);

        // ... build order items, calculate total (same as your existing code) ..
        Order savedOrder = orderRepository.save(order);

        // BEFORE: deliveryService.createDeliveryForOrder(savedOrder) — synchronous, blocking
        // AFTER: publish event and return immediately
        OrderPlacedEvent event = OrderPlacedEvent.builder().
                orderId(savedOrder.getId())
                .customerId(savedOrder.getCustomerId())
                .customerName(savedOrder.getCustomerName())
                .restaurantId(savedOrder.getRestaurantId())
                .restaurantName(savedOrder.getRestaurantName())
                .deliveryAddress(savedOrder.getDeliveryAddress())
                .estimatedDeliveryTime(savedOrder.getEstimatedDeliveryTime())
                .restaurantAddress(savedOrder.getRestaurantAddress())
                .build();

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.ORDER_EXCHANGE,
                RabbitMQConfig.ORDER_PLACED_ROOT_KEY,
                event);

        return OrderResponse.fromEntity(savedOrder);

    }


    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));
        return OrderResponse.fromEntity(order);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getCustomerOrders(String username) {
        CustomerResponse customer = customerService.getName(username);
        return orderRepository.findByCustomerIdOrderByCreatedAtDesc(customer.getId())
                .stream().map(OrderResponse::fromEntity).toList();

    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getRestaurantOrders(Long restaurantId) {
        return orderRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurantId)
                .stream().map(OrderResponse::fromEntity).toList();
    }

    @Transactional
    public OrderResponse updateOrderStatus(Long orderId, String status) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        Order.OrderStatus newStatus = Order.OrderStatus.valueOf(status.toUpperCase());
        order.setStatus(newStatus);

        return OrderResponse.fromEntity(orderRepository.save(order));
    }

    @Transactional
    public OrderResponse cancelOrder(Long orderId, String username) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));
        CustomerResponse customer = customerService.getName(username);
//        // MONOLITH: cross-domain entity check
        if (!order.getCustomerName().equals(customer.getUsername())) {
            throw new UnauthorizedException("You can only cancel your own orders");
        }

        if (order.getStatus() != Order.OrderStatus.PLACED
                && order.getStatus() != Order.OrderStatus.CONFIRMED) {
            throw new IllegalStateException("Cannot cancel order in status: " + order.getStatus());
        }


        order.setStatus(Order.OrderStatus.CANCELLED);

        OrderCancelledEvent event = OrderCancelledEvent.builder()
                .orderId(order.getId())
                .customerId(order.getCustomerId())
                .build();

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.ORDER_EXCHANGE,
                RabbitMQConfig.ORDER_CANCELLED_KEY,
                event
        );

        return OrderResponse.fromEntity(orderRepository.save(order));
    }
}
