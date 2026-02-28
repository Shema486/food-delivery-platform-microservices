package restaurant_service.controller;


import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import restaurant_service.dto.MenuItemRequest;
import restaurant_service.dto.MenuItemResponse;
import restaurant_service.dto.RestaurantRequest;
import restaurant_service.dto.RestaurantResponse;
import restaurant_service.service.RestaurantService;

import java.util.List;

@RestController
@RequestMapping("/api/restaurants")
public class RestaurantController {

    private final RestaurantService restaurantService;

    public RestaurantController(RestaurantService restaurantService) {
        this.restaurantService = restaurantService;
    }

    // ---- Public endpoints (no auth required) ----

    @GetMapping("/search/city/{city}")
    public ResponseEntity<List<RestaurantResponse>> searchByCity(@PathVariable String city) {
        return ResponseEntity.ok(restaurantService.searchByCity(city));
    }

    @GetMapping("/search/cuisine/{type}")
    public ResponseEntity<List<RestaurantResponse>> searchByCuisine(@PathVariable String type) {
        return ResponseEntity.ok(restaurantService.searchByCuisine(type));
    }

    @GetMapping("/search/all")
    public ResponseEntity<List<RestaurantResponse>> getAllActive() {
        return ResponseEntity.ok(restaurantService.getAllActive());
    }

    @GetMapping("/{id}")
    public ResponseEntity<RestaurantResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(restaurantService.getById(id));
    }

    @GetMapping("/{id}/menu")
    public ResponseEntity<List<MenuItemResponse>> getMenu(@PathVariable Long id) {
        return ResponseEntity.ok(restaurantService.getMenu(id));
    }

    // ---- Authenticated endpoints (restaurant owner) ----

    @PostMapping
    public ResponseEntity<RestaurantResponse> create(
            Authentication auth, @Valid @RequestBody RestaurantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(restaurantService.createRestaurant(auth.getName(), request));
    }

    @PostMapping("/{restaurantId}/menu")
    public ResponseEntity<MenuItemResponse> addMenuItem(
            @PathVariable Long restaurantId,
            Authentication auth,
            @Valid @RequestBody MenuItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(restaurantService.addMenuItem(restaurantId, auth.getName(), request));
    }
//
    @PutMapping("/menu/{itemId}")
    public ResponseEntity<MenuItemResponse> updateMenuItem(
            @PathVariable Long itemId,
            Authentication auth,
            @Valid @RequestBody MenuItemRequest request) {
        return ResponseEntity.ok(restaurantService.updateMenuItem(itemId, auth.getName(), request));
    }
//
    @PatchMapping("/menu/{itemId}/toggle")
    public ResponseEntity<Void> toggleAvailability(
            @PathVariable Long itemId, Authentication auth) {
        restaurantService.toggleMenuItemAvailability(itemId, auth.getName());
        return ResponseEntity.noContent().build();
    }




    // ---- Lightweight endpoints for inter-service communication (internal use) ----

        @GetMapping("/{id}")
        public ResponseEntity<RestaurantResponse> getRestaurantById(@PathVariable Long id) {
            return ResponseEntity.ok(restaurantService.findEntityById(id));
        }

    @GetMapping("/menu-items/{id}")
    public ResponseEntity<MenuItemResponse> getMenuItem(@PathVariable Long id) {
        return ResponseEntity.ok(restaurantService.findMenuItemById(id));
    }

//         Lightweight validation endpoint (best practice)
        @GetMapping("/{id}/exists")
        public ResponseEntity<Boolean> exists(@PathVariable Long id) {
            return ResponseEntity.ok(restaurantService.existsById(id));
        }

}
