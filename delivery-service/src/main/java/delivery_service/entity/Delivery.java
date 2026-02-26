package delivery_service.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Delivery entity — part of the Delivery domain.
 *
 * MONOLITH PROBLEM: Direct @OneToOne to Order entity and
 * delivery assignment happens SYNCHRONOUSLY inside the
 * OrderService.placeOrder() method. This blocks the order
 * response until delivery is assigned.
 *
 * In microservices:
 *  - Store orderId as a Long reference
 *  - Delivery Service subscribes to OrderPlacedEvent via RabbitMQ
 *  - Assignment happens ASYNCHRONOUSLY after the order is confirmed
 *  - Delivery Service publishes DeliveryStatusUpdatedEvent
 */
@Entity
@Table(name = "deliveries")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Delivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryStatus status;

    private String driverName;
    private String driverPhone;

    private String pickupAddress;
    private String deliveryAddress;

    private LocalDateTime assignedAt;
    private LocalDateTime pickedUpAt;
    private LocalDateTime deliveredAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Stores the ID of the order assigned to this delivery
    private Long orderId;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) status = DeliveryStatus.PENDING;
    }

    public enum DeliveryStatus {
        PENDING,
        ASSIGNED,
        PICKED_UP,
        IN_TRANSIT,
        DELIVERED,
        FAILED
    }
}