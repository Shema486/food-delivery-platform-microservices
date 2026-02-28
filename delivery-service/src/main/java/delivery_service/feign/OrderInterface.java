package delivery_service.feign;

import delivery_service.dto.external.OrderResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient("ORDER-SERVICE")
public interface OrderInterface {
    @GetMapping("/{id}")
    OrderResponse getById(@PathVariable Long id);

}
