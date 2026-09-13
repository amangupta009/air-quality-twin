package com.capstone.airquality.api;

import com.capstone.airquality.domain.AlertEvent;
import com.capstone.airquality.domain.VentilationAction;
import com.capstone.airquality.repo.AlertEventRepository;
import com.capstone.airquality.repo.VentilationActionRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Cross-room audit view: every action and every alert across ALL rooms,
 * newest first. Used by the "All" window in the Recent Activity panel so
 * nothing is left out regardless of which room the user logged into.
 */
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final VentilationActionRepository actionRepo;
    private final AlertEventRepository alertRepo;

    public AuditController(VentilationActionRepository actionRepo, AlertEventRepository alertRepo) {
        this.actionRepo = actionRepo;
        this.alertRepo = alertRepo;
    }

    @GetMapping("/actions")
    public List<VentilationAction> actions() {
        return actionRepo.findAllByOrderByActedAtDesc();
    }

    @GetMapping("/alerts")
    public List<AlertEvent> alerts() {
        return alertRepo.findAllByOrderByTriggeredAtDesc();
    }
}
