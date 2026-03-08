package restaurant_service.fallback;

import org.springframework.stereotype.Component;
import restaurant_service.dto.external.CustomerResponse;
import restaurant_service.feign.CustomerInterface;

@Component
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
