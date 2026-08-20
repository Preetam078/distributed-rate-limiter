package com.ratelimiter.configservice.model;

import com.ratelimiter.common.enums.Algorithm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RuleRepository extends JpaRepository<RateLimitRule, Long> {

    List<RateLimitRule> findByDomainAndEnabledTrue(String domain);

    List<RateLimitRule> findByEnabledTrue();

    @Query("SELECT r FROM RateLimitRule r WHERE r.enabled = true AND r.path LIKE %?1% ORDER BY r.priority DESC")
    List<RateLimitRule> findMatchingRules(String path);
}
