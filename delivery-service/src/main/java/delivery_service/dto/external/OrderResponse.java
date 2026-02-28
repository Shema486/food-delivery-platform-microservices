package delivery_service.dto.external;


import lombok.Data;


import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class OrderResponse {
    private Long id;
    private String status;
    private BigDecimal totalAmount;
    private BigDecimal deliveryFee;
    private String deliveryAddress;
    private String specialInstructions;
    private LocalDateTime createdAt;
    private LocalDateTime estimatedDeliveryTime;
    private List<OrderItemDetail> items;


    private Long customerId;
    private String customerName;
    private Long restaurantId;
    private String restaurantName;


    private String deliveryStatus;
    private String driverName;
    private String driverPhone;



    @Data
    public static class OrderItemDetail {
        private Long id;
        private String itemName;
        private int quantity;
        private BigDecimal unitPrice;
        private BigDecimal subtotal;
    }


}

