package order_service.dto.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
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
