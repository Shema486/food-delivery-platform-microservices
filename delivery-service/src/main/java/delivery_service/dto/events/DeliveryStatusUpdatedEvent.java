package delivery_service.dto.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryStatusUpdatedEvent {
    private Long deliveryId;
    private Long orderId;
    private String newStatus;       // ASSIGNED, PICKED_UP, DELIVERED, FAILED
    private LocalDateTime updatedAt;
}
