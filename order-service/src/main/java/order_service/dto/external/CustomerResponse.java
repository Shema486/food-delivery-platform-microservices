package order_service.dto.external;


import lombok.Data;

import java.time.LocalDateTime;


@Data
public class CustomerResponse {
    private Long id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String phone;
    private String deliveryAddress;
    private String city;
    private String role;
    private LocalDateTime createdAt;
    private int orderCount;
}
