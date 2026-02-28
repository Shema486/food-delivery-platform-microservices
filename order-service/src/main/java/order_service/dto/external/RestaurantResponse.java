package order_service.dto.external;

import lombok.Data;

@Data
public class RestaurantResponse {

    private Long id;
    private String name;
    private String address;
    private String city;
    private boolean active;
    private int estimatedDeliveryMinutes;

}


