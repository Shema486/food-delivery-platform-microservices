package delivery_service.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient("RESTAURANT-SERVICE")
public interface RestaurantInterface {
    @GetMapping("/{id}")
    RestaurantResponse getById(@PathVariable Long id)    ;
    @GetMapping("/{id}")
    RestaurantResponse getRestaurantById(@PathVariable Long id) ;

    @GetMapping("/menu-items/{id}")
    MenuItemResponse getMenuItem(@PathVariable Long id) ;

    //         Lightweight validation endpoint (best practice)
    @GetMapping("/{id}/exists")
    Boolean exists(@PathVariable Long id) ;
}
