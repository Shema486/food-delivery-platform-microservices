package order_service.dto.external;


import lombok.Data;


import java.math.BigDecimal;

@Data
public class MenuItemResponse {

    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    private String category;
    private boolean available;
    private String imageUrl;
    private Long restaurantId;
    private String restaurantName;
}







