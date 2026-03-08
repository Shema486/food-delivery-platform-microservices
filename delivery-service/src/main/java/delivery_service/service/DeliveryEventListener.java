package delivery_service.service;



import delivery_service.config.RabbitMQConfig;
import delivery_service.dto.events.OrderCancelledEvent;
import delivery_service.dto.events.OrderPlacedEvent;
import delivery_service.entity.Delivery;
import delivery_service.repository.DeliveryRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;


@Service
@AllArgsConstructor
@Slf4j
public class DeliveryEventListener {

    private final DeliveryRepository deliveryRepository;


    private static final String[] DRIVERS = {
            "Carlos Martinez", "Sarah Johnson", "Mike Chen", "Priya Patel", "James Wilson"
    };
    private static final String[] PHONES = {
            "+1-555-0101", "+1-555-0102", "+1-555-0103", "+1-555-0104", "+1-555-0105"
    };

    @RabbitListener(queues = RabbitMQConfig.DELIVERY_ORDER_PLACED_QUEUE)
    public void handleOrderPlacedEvent(OrderPlacedEvent event) {
        int driverIndex = (int) (Math.random() * DRIVERS.length);

        Delivery delivery = Delivery.builder()
                .orderId(event.getOrderId())
                .status(Delivery.DeliveryStatus.ASSIGNED)
                .driverName(DRIVERS[driverIndex])
                .driverPhone(PHONES[driverIndex])
                .pickupAddress(event.getRestaurantAddress())   // data came with the event
                .deliveryAddress(event.getDeliveryAddress())   // data came with the event
                .assignedAt(LocalDateTime.now())
                .build();

        deliveryRepository.save(delivery);

        // No Feign calls needed — everything was in the event
        log.info("Delivery assigned for order #{} to driver {} for customer {}",
                event.getOrderId(), DRIVERS[driverIndex], event.getCustomerName());
    }

    @RabbitListener(queues = RabbitMQConfig.DELIVERY_ORDER_CANCELLED_QUEUE)
    public void handleCancelledDelivery(OrderCancelledEvent event) {

            deliveryRepository.findByOrderId(event.getOrderId()).ifPresent(delivery -> {
                delivery.setStatus(Delivery.DeliveryStatus.FAILED);
                deliveryRepository.save(delivery);
                log.info("Delivery for order #{} has been cancelled", event.getOrderId());
            });
        }
}
