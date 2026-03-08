package order_service.circuitbreaker;

import order_service.dto.external.CustomerResponse;
import order_service.feign.CustomerInterface;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CustomerClientFallback implements CustomerInterface {
    @Override
    public CustomerResponse getName(String username) throws RuntimeException {
        throw new RuntimeException(
                "Customer Service is unavailable. Cannot verify owner: " + username
        );
    }

    @Override
    public Boolean existsById(Long id) throws RuntimeException {
        throw new RuntimeException(
                "Customer Service is unavailable. Cannot verify id: " + id
        );
    }

    @Override
    public CustomerResponse getById(Long id) throws RuntimeException {
        throw new RuntimeException(
                "Customer Service is unavailable. doesn't exist: " + id
        );
    }
}
