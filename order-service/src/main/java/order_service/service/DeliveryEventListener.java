package order_service.service;

import lombok.AllArgsConstructor;

import order_service.config.RabbitMQConfig;
import order_service.dto.events.DeliveryStatusUpdatedEvent;
import order_service.entity.Order;
import order_service.repository.OrderRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;


@AllArgsConstructor
@Service
public class DeliveryEventListener {
    private final OrderRepository orderRepository;

    @RabbitListener(queues = RabbitMQConfig.ORDER_DELIVERY_UPDATED_STATUS_QUEUE)
    public void handleDeliveryStatusUpdated(DeliveryStatusUpdatedEvent event){

        orderRepository.findById(event.getOrderId()).ifPresent(order -> {
            if ("DELIVERED".equals(event.getNewStatus())) {
                order.setStatus(Order.OrderStatus.DELIVERED);
            } else if ("PICKED_UP".equals(event.getNewStatus())) {
                order.setStatus(Order.OrderStatus.OUT_FOR_DELIVERY);
            } else if ("FAILED".equals(event.getNewStatus())) {
                order.setStatus(Order.OrderStatus.CANCELLED);
            }
            orderRepository.save(order);
        });

    }

}
