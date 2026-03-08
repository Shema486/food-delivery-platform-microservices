package customer_service.controller;


import customer_service.dto.CustomerResponse;
import customer_service.dto.RegisterRequest;
import customer_service.entity.CustomerEntity;
import customer_service.service.CustomerService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping("/me")
    public ResponseEntity<CustomerResponse> getMyProfile(Authentication auth) {
        return ResponseEntity.ok(customerService.getProfile(auth.getName()));

    }

    @GetMapping("/{id}")
    public ResponseEntity<CustomerResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(customerService.getById(id));
    }


    @PutMapping("/me")
    public ResponseEntity<CustomerResponse> updateProfile(
            Authentication auth, @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(customerService.updateProfile(auth.getName(), request));


    }

    //MICROSERVICES: This endpoint is for internal use by other services, not exposed to clients.
    @GetMapping("/username/{username}")
    public ResponseEntity<CustomerResponse> getName(@PathVariable  String username) {
        return ResponseEntity.ok(customerService.findEntityByUsername(username));
    }

    @GetMapping("/{id}/exists")
    public ResponseEntity<Boolean> existsById(@PathVariable Long id) {
        return ResponseEntity.ok(customerService.existById(id));
    }


}
