package delivery_service.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    private static final String ORDER_EXCHANGE = "order.exchange";
    public static final String DELIVERY_EXCHANGE    = "delivery.exchange";
    private static final String ORDER_PLACED_ROOT_KEY    = "order.placed";
    public static final String DELIVERY_STATUS_ROOT_KEY = "delivery.status.updated";
    public static final String DELIVERY_ORDER_PLACED_QUEUE = "delivery.order.placed.queue";
    public static final String DELIVERY_ORDER_CANCELLED_QUEUE = "delivery.order.cancelled";
    public static final String ORDER_CANCELLED_KEY = "order.cancelled";


    @Bean
    public TopicExchange orderExchange() {return new TopicExchange(ORDER_EXCHANGE);}

    @Bean
    public TopicExchange deliveryExchange() {return new TopicExchange(DELIVERY_EXCHANGE);}

    @Bean
    public Queue deliveryOrderPlacedQueue() {return new Queue(DELIVERY_ORDER_PLACED_QUEUE);}

    @Bean Queue orderCancelledQueue() {return new Queue(DELIVERY_ORDER_PLACED_QUEUE);}

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public Binding bindOrderPlaced(Queue deliveryOrderPlacedQueue, TopicExchange orderExchange) {
        return BindingBuilder
                .bind(deliveryOrderPlacedQueue)
                .to(orderExchange)
                .with(ORDER_PLACED_ROOT_KEY );
    }
    @Bean
    public Binding bindCancelledPlaced(Queue  orderCancelledQueue, TopicExchange orderExchange) {
        return BindingBuilder
                .bind( orderCancelledQueue)
                .to(orderExchange)
                .with(ORDER_CANCELLED_KEY);
    }
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }
}
