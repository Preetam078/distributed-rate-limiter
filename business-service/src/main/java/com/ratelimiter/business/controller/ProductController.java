package com.ratelimiter.business.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAllProducts() {
        List<Map<String, Object>> products = List.of(
                Map.of("id", 1, "name", "Laptop Pro", "price", 1299.99, "stock", 50),
                Map.of("id", 2, "name", "Phone Max", "price", 899.99, "stock", 200),
                Map.of("id", 3, "name", "Tablet Air", "price", 599.99, "stock", 150)
        );
        return ResponseEntity.ok(products);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getProductById(@PathVariable Long id) {
        Map<String, Object> product = Map.of(
                "id", id,
                "name", "Product_" + id,
                "price", 99.99,
                "stock", 100
        );
        return ResponseEntity.ok(product);
    }
}
