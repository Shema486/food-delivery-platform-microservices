package restaurant_service.dto.external;

import lombok.AllArgsConstructor;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RestaurantCreatedEvent {
    private Long restaurantId;
    private String restaurantName;
    private Long ownerId;
    private String ownerUsername;
}
