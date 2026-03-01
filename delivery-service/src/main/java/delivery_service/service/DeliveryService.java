package delivery_service.service;


import delivery_service.config.RabbitMQConfig;
import delivery_service.dto.DeliveryResponse;
import delivery_service.dto.events.DeliveryStatusUpdatedEvent;
import delivery_service.entity.Delivery;
import delivery_service.exception.ResourceNotFoundException;
import delivery_service.repository.DeliveryRepository;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

/**
 * MONOLITH COUPLING: DeliveryService directly accesses Order entity
 * (and through it, Customer and Restaurant entities).
 *
 * In microservices:
 *  - Delivery Service subscribes to OrderPlacedEvent via RabbitMQ
 *  - Stores orderId, customerAddress, restaurantAddress as local data
 *  - Publishes DeliveryStatusUpdatedEvent when status changes
 *  - No direct dependency on Order, Customer, or Restaurant entities
 */
@Service
@AllArgsConstructor
public class DeliveryService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryService.class);

    private final DeliveryRepository deliveryRepository;
    private final RabbitTemplate rabbitTemplate;



    @Transactional(readOnly = true)
    public DeliveryResponse getByOrderId(Long orderId) {
        Delivery delivery = deliveryRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery", "orderId", orderId));
        return DeliveryResponse.fromEntity(delivery);
    }

    @Transactional(readOnly = true)
    public DeliveryResponse getById(Long deliveryId) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery", "id", deliveryId));
        return DeliveryResponse.fromEntity(delivery);
    }

    @Transactional(readOnly = true)
    public List<DeliveryResponse> getByStatus(String status) {
        Delivery.DeliveryStatus deliveryStatus = Delivery.DeliveryStatus.valueOf(status.toUpperCase());
        return deliveryRepository.findByStatus(deliveryStatus)
                .stream().map(DeliveryResponse::fromEntity).toList();
    }

    @Transactional
    public DeliveryResponse updateStatus(Long deliveryId, Delivery.DeliveryStatus newStatus) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery", "id", deliveryId));

        delivery.setStatus(newStatus);

        switch (newStatus) {
            case PICKED_UP -> delivery.setPickedUpAt(LocalDateTime.now());
            case DELIVERED -> delivery.setDeliveredAt(LocalDateTime.now());
            default -> {}
        }

        DeliveryStatusUpdatedEvent event= DeliveryStatusUpdatedEvent.builder()
                .deliveryId(delivery.getId())
                .orderId(delivery.getOrderId())
                .newStatus(newStatus.name())
                .updatedAt(LocalDateTime.now())
                .build();

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.DELIVERY_EXCHANGE,
                RabbitMQConfig.DELIVERY_STATUS_ROOT_KEY,
                event
        );
        return DeliveryResponse.fromEntity(deliveryRepository.save(delivery));
    }


}
