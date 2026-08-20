package com.ratelimiter.configservice.controller;

import com.ratelimiter.configservice.model.RateLimitRule;
import com.ratelimiter.configservice.service.RuleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/rules")
public class RuleController {

    private static final Logger log = LoggerFactory.getLogger(RuleController.class);

    private final RuleService ruleService;

    public RuleController(RuleService ruleService) {
        this.ruleService = ruleService;
    }

    @GetMapping
    public ResponseEntity<List<RateLimitRule>> getAllRules() {
        log.debug("Fetching all rules");
        return ResponseEntity.ok(ruleService.getAllRules());
    }

    @GetMapping("/{id}")
    public ResponseEntity<RateLimitRule> getRuleById(@PathVariable Long id) {
        log.debug("Fetching rule by id: {}", id);
        return ResponseEntity.ok(ruleService.getRuleById(id));
    }

    @GetMapping("/domain/{domain}")
    public ResponseEntity<List<RateLimitRule>> getRulesByDomain(@PathVariable String domain) {
        log.debug("Fetching rules for domain: {}", domain);
        return ResponseEntity.ok(ruleService.getRulesByDomain(domain));
    }

    @PostMapping
    public ResponseEntity<RateLimitRule> createRule(@RequestBody RateLimitRule rule) {
        log.info("Creating rule: path={}, method={}", rule.getPath(), rule.getMethod());
        RateLimitRule created = ruleService.createRule(rule);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<RateLimitRule> updateRule(@PathVariable Long id, @RequestBody RateLimitRule rule) {
        log.info("Updating rule: {}", id);
        return ResponseEntity.ok(ruleService.updateRule(id, rule));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRule(@PathVariable Long id) {
        log.info("Deleting rule: {}", id);
        ruleService.deleteRule(id);
        return ResponseEntity.noContent().build();
    }
}
