package com.capstone.airquality.repo;

import com.capstone.airquality.domain.AlertEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AlertEventRepository extends JpaRepository<AlertEvent, Long> {

    Optional<AlertEvent> findTopByRoomIdAndMetricAndResolvedAtIsNullOrderByTriggeredAtDesc(
            String roomId, String metric);

    List<AlertEvent> findByRoomIdOrderByTriggeredAtDesc(String roomId);

    List<AlertEvent> findAllByOrderByTriggeredAtDesc();

    List<AlertEvent> findByTriggeredAtBetween(Instant from, Instant to);
}
