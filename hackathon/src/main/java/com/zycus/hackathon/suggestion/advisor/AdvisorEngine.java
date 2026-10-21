package com.zycus.hackathon.suggestion.advisor;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.zycus.hackathon.suggestion.signal.ProductContext;

import lombok.extern.slf4j.Slf4j;

/**
 * Single entry point for advice. Holds every {@link Advisor} strategy and routes to the active one,
 * which operators can switch at runtime without a restart.
 */
@Service
@Slf4j
public class AdvisorEngine {

    private final Map<AdvisorStrategy, Advisor> advisors;
    private volatile AdvisorStrategy active;

    AdvisorEngine(List<Advisor> advisors, AdvisorProperties props) {
        Map<AdvisorStrategy, Advisor> byStrategy = new EnumMap<>(AdvisorStrategy.class);
        advisors.forEach(advisor -> byStrategy.put(advisor.strategy(), advisor));
        if (!byStrategy.keySet().containsAll(Set.of(AdvisorStrategy.values()))) {
            throw new IllegalStateException("Missing advisor for some strategies; found " + byStrategy.keySet());
        }
        this.advisors = byStrategy;
        this.active = props.strategy();
    }

    public Advice advise(ProductContext context, List<ReviewFeedback> recentRejections) {
        return advisors.get(active).advise(context, recentRejections);
    }

    public AdvisorStrategy activeStrategy() {
        return active;
    }

    /** Affects later evaluations only; suggestions already saved keep their source. */
    public AdvisorStrategy switchTo(AdvisorStrategy strategy) {
        log.info("Advisor strategy switched from {} to {}", active, strategy);
        active = strategy;
        return active;
    }
}
