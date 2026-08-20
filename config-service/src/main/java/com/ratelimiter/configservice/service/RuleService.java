package com.ratelimiter.configservice.service;

import com.ratelimiter.configservice.model.RateLimitRule;
import com.ratelimiter.configservice.model.RuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RuleService {

    private static final Logger log = LoggerFactory.getLogger(RuleService.class);

    private final RuleRepository repository;

    public RuleService(RuleRepository repository) {
        this.repository = repository;
    }

    public List<RateLimitRule> getAllRules() {
        return repository.findAll();
    }

    public List<RateLimitRule> getEnabledRules() {
        return repository.findByEnabledTrue();
    }

    public List<RateLimitRule> getRulesByDomain(String domain) {
        return repository.findByDomainAndEnabledTrue(domain);
    }

    public RateLimitRule getRuleById(Long id) {
        return repository.findById(id).orElseThrow(() ->
                new RuntimeException("Rule not found with id: " + id));
    }

    public RateLimitRule createRule(RateLimitRule rule) {
        RateLimitRule saved = repository.save(rule);
        log.info("Created rule: {} for path={}", saved.getId(), saved.getPath());
        return saved;
    }

    public RateLimitRule updateRule(Long id, RateLimitRule updated) {
        RateLimitRule existing = getRuleById(id);
        existing.setDomain(updated.getDomain());
        existing.setPath(updated.getPath());
        existing.setMethod(updated.getMethod());
        existing.setRequestsPerWindow(updated.getRequestsPerWindow());
        existing.setWindowSizeSeconds(updated.getWindowSizeSeconds());
        existing.setKeyResolverType(updated.getKeyResolverType());
        existing.setAlgorithm(updated.getAlgorithm());
        existing.setFailStrategy(updated.getFailStrategy());
        existing.setPriority(updated.getPriority());
        existing.setEnabled(updated.isEnabled());
        RateLimitRule saved = repository.save(existing);
        log.info("Updated rule: {}", saved.getId());
        return saved;
    }

    public void deleteRule(Long id) {
        repository.deleteById(id);
        log.info("Deleted rule: {}", id);
    }
}
