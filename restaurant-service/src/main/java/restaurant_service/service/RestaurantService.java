package restaurant_service.service;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import restaurant_service.config.RabbitMQConfig;
import restaurant_service.dto.MenuItemRequest;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import restaurant_service.dto.MenuItemResponse;
import restaurant_service.dto.RestaurantRequest;
import restaurant_service.dto.RestaurantResponse;
import restaurant_service.dto.external.CustomerResponse;
import restaurant_service.dto.external.RestaurantCreatedEvent;
import restaurant_service.entity.MenuItem;
import restaurant_service.entity.Restaurant;
import restaurant_service.exception.ResourceNotFoundException;
import restaurant_service.exception.UnauthorizedException;
import restaurant_service.feign.CustomerInterface;
import restaurant_service.repository.MenuItemRepository;
import restaurant_service.repository.RestaurantRepository;

import java.util.List;

/**
 * MONOLITH COUPLING: This service directly accesses CustomerRepository
 * to validate restaurant ownership. In microservices, it should call
 * Customer Service via Feign to validate the owner.
 */
@Service
@AllArgsConstructor
public class RestaurantService {

    private final RestaurantRepository restaurantRepository;
    private final MenuItemRepository menuItemRepository;
    private final CustomerInterface customerServices;
    private final RabbitTemplate rabbitTemplate;
//    private final CustomerRepository customerRepository; // CROSS-DOMAIN DEPENDENCY



    @Transactional
    public RestaurantResponse createRestaurant(String ownerUsername, RestaurantRequest request) {
        // MONOLITH: directly accessing Customer entity from Restaurant domain
        CustomerResponse owner = customerServices.getName(ownerUsername);

        Restaurant restaurant = Restaurant.builder()
                .name(request.getName())
                .description(request.getDescription())
                .cuisineType(request.getCuisineType())
                .address(request.getAddress())
                .city(request.getCity())
                .phone(request.getPhone())
                .estimatedDeliveryMinutes(request.getEstimatedDeliveryMinutes())
                .ownerId(owner.getId()) // MONOLITH: direct entity reference across domains
                .build();

        Restaurant savedRestaurant = restaurantRepository.save(restaurant);

        // Publish — Customer Service will promote the owner role on its own
        RestaurantCreatedEvent event = new RestaurantCreatedEvent(
                savedRestaurant.getId(), // restaurantId will be set after saving
                request.getName(),
                owner.getId(),
                ownerUsername
        );
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.RESTAURANT_EXCHANGE,
                RabbitMQConfig.RESTAURANT_CREATED_KEY,
                event);
        return RestaurantResponse.fromEntity(savedRestaurant);

    }

    @Transactional(readOnly = true)
    public RestaurantResponse getById(Long id) {
        Restaurant restaurant = restaurantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", "id", id));
        return RestaurantResponse.fromEntity(restaurant);
    }

    @Transactional(readOnly = true)
    public List<RestaurantResponse> searchByCity(String city) {
        return restaurantRepository.findByCityIgnoreCaseAndActiveTrue(city)
                .stream().map(RestaurantResponse::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public List<RestaurantResponse> searchByCuisine(String cuisineType) {
        return restaurantRepository.findByCuisineTypeIgnoreCaseAndActiveTrue(cuisineType)
                .stream().map(RestaurantResponse::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public List<RestaurantResponse> getAllActive() {
        return restaurantRepository.findByActiveTrue()
                .stream().map(RestaurantResponse::fromEntity).toList();
    }

    // ---- Menu Item management ----

    @Transactional
    public MenuItemResponse addMenuItem(Long restaurantId, String ownerUsername, MenuItemRequest request) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", "id", restaurantId));

        CustomerResponse owner = customerServices.getName(ownerUsername);

        // MONOLITH: cross-domain ownership check via entity traversal
        if (!restaurant.getOwnerId().equals(owner.getId())) {
            throw new UnauthorizedException("You don't own this restaurant");
        }

        MenuItem item = MenuItem.builder()
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .category(request.getCategory())
                .imageUrl(request.getImageUrl())
                .restaurant(restaurant)
                .build();

        return MenuItemResponse.fromEntity(menuItemRepository.save(item));
    }

    @Transactional(readOnly = true)
    public List<MenuItemResponse> getMenu(Long restaurantId) {
        return menuItemRepository.findByRestaurantIdAndAvailableTrue(restaurantId)
                .stream().map(MenuItemResponse::fromEntity).toList();
    }

    @Transactional
    public MenuItemResponse updateMenuItem(Long itemId, String ownerUsername, MenuItemRequest request) {
        MenuItem item = menuItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("MenuItem", "id", itemId));
        CustomerResponse owner = customerServices.getName(ownerUsername);

        // MONOLITH: cross-domain ownership check
        if (!item.getRestaurant().getOwnerId().equals(owner.getId())) {
            throw new UnauthorizedException("You don't own this restaurant");
        }

        if (request.getName() != null) item.setName(request.getName());
        if (request.getDescription() != null) item.setDescription(request.getDescription());
        if (request.getPrice() != null) item.setPrice(request.getPrice());
        if (request.getCategory() != null) item.setCategory(request.getCategory());

        return MenuItemResponse.fromEntity(menuItemRepository.save(item));
    }

    @Transactional
    public void toggleMenuItemAvailability(Long itemId, String ownerUsername) {
        MenuItem item = menuItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("MenuItem", "id", itemId));

        CustomerResponse owner = customerServices.getName(ownerUsername);

        if (!item.getRestaurant().getOwnerId().equals(owner.getId())) {
            throw new UnauthorizedException("You don't own this restaurant");
        }

        item.setAvailable(!item.isAvailable());
        menuItemRepository.save(item);
    }

    // Used by OrderService — MONOLITH COUPLING
    public RestaurantResponse findEntityById(Long id) {
        Restaurant restaurant = restaurantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", "id", id));
        return RestaurantResponse.fromEntity(restaurant);
    }

    public MenuItemResponse findMenuItemById(Long id) {
        MenuItem menuItem = menuItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MenuItem", "id", id));
        return MenuItemResponse.fromEntity(menuItem);
    }

    public Boolean existsById(Long id) {
        return restaurantRepository.existsById(id);
    }
}
