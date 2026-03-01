package delivery_service.dto.events;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class OrderPlacedEvent {
    private Long orderId;
    private Long customerId;
    private String customerName;
    private String deliveryAddress;     // Where to deliver
    private Long restaurantId;
    private String restaurantName;
    private String restaurantAddress;   // Pickup address for driver
    private LocalDateTime estimatedDeliveryTime;
}
