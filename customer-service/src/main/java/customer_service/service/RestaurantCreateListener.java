package customer_service.service;

import customer_service.config.RabbitMQConfig;

import customer_service.dto.RestaurantCreatedEvent;
import customer_service.entity.CustomerEntity;
import customer_service.repository.CustomerRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
@Slf4j
public class RestaurantCreateListener {
    private  final CustomerRepository customerRepository;

    @RabbitListener(queues = RabbitMQConfig.CUSTOMER_RESTAURANT_QUEUE)
    public void handleUpdateRestaurantStatus(RestaurantCreatedEvent event){
        // In a real application, you would update the customer's role to OWNER
        // Here we just print the event for demonstration
        log.info("Received restaurant created event:{} ",  event);
        customerRepository.findById(event.getOwnerId()).ifPresent(customer -> {
            if (customer.getRole() == CustomerEntity.Role.CUSTOMER) {
                customer.setRole(CustomerEntity.Role.RESTAURANT_OWNER);
                customerRepository.save(customer);
                log.info("Customer {} promoted to RESTAURANT_OWNER", event.getOwnerUsername());
            }
        });

    }
}
