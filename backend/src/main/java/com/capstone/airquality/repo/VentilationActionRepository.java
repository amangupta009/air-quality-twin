package com.capstone.airquality.repo;

import com.capstone.airquality.domain.VentilationAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface VentilationActionRepository extends JpaRepository<VentilationAction, Long> {

    List<VentilationAction> findByRoomIdOrderByActedAtDesc(String roomId);

    List<VentilationAction> findByActedAtBetweenOrderByActedAtAsc(Instant from, Instant to);

    List<VentilationAction> findAllByOrderByActedAtDesc();
}
