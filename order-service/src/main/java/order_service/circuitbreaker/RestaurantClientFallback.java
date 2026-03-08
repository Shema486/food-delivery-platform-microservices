package order_service.circuitbreaker;


import order_service.dto.external.MenuItemResponse;
import order_service.dto.external.RestaurantResponse;

import order_service.feign.RestaurantInterface;
import org.springframework.stereotype.Component;

@Component
public class RestaurantClientFallback implements RestaurantInterface {


    @Override
    public RestaurantResponse getRestaurantById(Long id)throws RuntimeException {
         throw new RuntimeException(
                "Restaurant Service is unavailable. doesn't exist: " + id
        );

    }

    @Override
    public MenuItemResponse getMenuItem(Long id)throws RuntimeException  {
        throw new RuntimeException(
                "Restaurant Service is unavailable. cannot verify menu item: " + id
        );

    }

    @Override
    public Boolean exists(Long id)throws RuntimeException {
        throw new RuntimeException(
                "Restaurant Service is unavailable. cannot verify restaurant: " + id
        );

    }
}
