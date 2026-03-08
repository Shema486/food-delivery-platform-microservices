package customer_service.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
        public   static final String RESTAURANT_EXCHANGE = "restaurant.exchange";
        private static final String CUSTOMER_EXCHANGE = "customer.exchange";
        public static final String RESTAURANT_CREATED_KEY = "restaurant.created";
        public static final String CUSTOMER_RESTAURANT_QUEUE = "customer.restaurant.queue";

        public  static final String CUSTOMER_TOPIC = "customer.restaurant.tzopic";


        @Bean
        public TopicExchange restaurantExchange() {
            return new TopicExchange(RESTAURANT_EXCHANGE);
        }

        @Bean
        public TopicExchange customerExchange() {
            return new TopicExchange(CUSTOMER_EXCHANGE);
        }

        @Bean
        public Queue customerRestaurantQueue() {
            return new Queue(CUSTOMER_RESTAURANT_QUEUE);
        }

        @Bean
        public MessageConverter messageConverter() {
            return new JacksonJsonMessageConverter();
        }

        @Bean
        public Binding customerRestaurantBinding(Queue customerRestaurantQueue, TopicExchange restaurantExchange) {
            return BindingBuilder
                    .bind(customerRestaurantQueue)
                    .to(restaurantExchange).with(RESTAURANT_CREATED_KEY);
        }

      @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
            RabbitTemplate template =new RabbitTemplate(connectionFactory);
            template.setMessageConverter(messageConverter());
            return template;
      }
}
