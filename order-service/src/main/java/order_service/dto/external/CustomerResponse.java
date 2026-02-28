package order_service.dto.external;


import lombok.Data;



@Data
public class CustomerResponse {
    private Long id;
    private String username;
    private String firstName;
    private String lastName;
    private String phone;
    private String deliveryAddress;
    private String city;
}
