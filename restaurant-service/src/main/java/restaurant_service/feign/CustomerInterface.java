package restaurant_service.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import restaurant_service.dto.external.CustomerResponse;
import restaurant_service.fallback.CustomerClientFallback;

@FeignClient(
        name = "CUSTOMER-SERVICE",
        url = "${services.customer.url}",
        fallback = CustomerClientFallback.class)

public interface CustomerInterface {
    @GetMapping("/api/customers/username/{username}")
    CustomerResponse getName(@PathVariable String username);

    @GetMapping("/api/customers/{id}/exists")
    Boolean existsById(@PathVariable Long id);

    @GetMapping("/api/customers/{id}")
    CustomerResponse getById(@PathVariable Long id) ;

}
