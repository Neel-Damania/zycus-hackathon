package com.zycus.hackathon.suggestion.advisor;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;

/** Shows and switches the active advisor strategy. */
@RestController
@RequestMapping("/api/advisor")
@RequiredArgsConstructor
class AdvisorController {

    private final AdvisorEngine engine;

    record AdvisorStatus(AdvisorStrategy strategy, List<AdvisorStrategy> available) {

        static AdvisorStatus of(AdvisorStrategy active) {
            return new AdvisorStatus(active, List.of(AdvisorStrategy.values()));
        }
    }

    record SwitchRequest(@NotNull AdvisorStrategy strategy) {
    }

    @GetMapping
    AdvisorStatus get() {
        return AdvisorStatus.of(engine.activeStrategy());
    }

    @PutMapping
    AdvisorStatus switchStrategy(@Valid @RequestBody SwitchRequest request) {
        return AdvisorStatus.of(engine.switchTo(request.strategy()));
    }
}
