package order_service.feign;

import org.springframework.cloud.openfeign.FeignClient;

@FeignClient("DELIVERY-SERVICE")
public interface DeliveryInterface {

}
