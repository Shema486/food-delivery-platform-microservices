package order_service.feign;

import order_service.circuitbreaker.RestaurantClientFallback;
import order_service.dto.external.MenuItemResponse;
import order_service.dto.external.RestaurantResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
        name="RESTAURANT-SERVICE",
        url = "${services.restaurant.url}",
        fallback = RestaurantClientFallback.class)
public interface RestaurantInterface {

    @GetMapping("/api/restaurants/{id}")
    RestaurantResponse getRestaurantById(@PathVariable Long id) ;

    @GetMapping("/api/restaurants/menu-items/{id}")
    MenuItemResponse getMenuItem(@PathVariable Long id) ;

    //  Lightweight validation endpoint (best practice)
    @GetMapping("/api/restaurants/{id}/exists")
    Boolean exists(@PathVariable Long id) ;
}
