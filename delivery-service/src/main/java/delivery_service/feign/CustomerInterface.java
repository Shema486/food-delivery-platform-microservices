package delivery_service.feign;

import delivery_service.dto.external.CustomerResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient("CUSTOMER-SERVICE")
public interface CustomerInterface {
    @GetMapping("/username/{username}")
    CustomerResponse getName(@PathVariable String username);

    @GetMapping("/{id}/exists")
    public ResponseEntity<Boolean> existsById(@PathVariable Long id);

    @GetMapping("/{id}")
    CustomerResponse getById(@PathVariable Long id) ;
}
