package order_service.dto.external;


import lombok.Data;


import java.math.BigDecimal;

@Data
public class MenuItemResponse {

    private Long id;
    private String name;
    private BigDecimal price;
    private boolean available;
}







