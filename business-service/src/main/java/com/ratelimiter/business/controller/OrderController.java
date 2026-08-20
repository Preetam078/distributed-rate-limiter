package com.ratelimiter.business.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAllOrders() {
        List<Map<String, Object>> orders = List.of(
                Map.of("id", 101, "product", "Laptop", "amount", 999.99, "status", "completed"),
                Map.of("id", 102, "product", "Phone", "amount", 699.99, "status", "pending"),
                Map.of("id", 103, "product", "Tablet", "amount", 449.99, "status", "shipped")
        );
        return ResponseEntity.ok(orders);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createOrder(@RequestBody Map<String, Object> order) {
        Map<String, Object> result = Map.of(
                "id", 201,
                "product", order.getOrDefault("product", "Unknown"),
                "amount", order.getOrDefault("amount", 0.0),
                "status", "created",
                "message", "Order created successfully"
        );
        return ResponseEntity.ok(result);
    }
}
