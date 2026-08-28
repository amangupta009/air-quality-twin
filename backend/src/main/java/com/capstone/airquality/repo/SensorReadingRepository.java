package com.capstone.airquality.repo;

import com.capstone.airquality.domain.SensorReadingEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

/** Time-series access for replay and daily summaries. */
public interface SensorReadingRepository extends JpaRepository<SensorReadingEntity, Long> {

    List<SensorReadingEntity> findByRoomIdAndRecordedAtBetweenOrderByRecordedAtAsc(
            String roomId, Instant from, Instant to);

    Page<SensorReadingEntity> findByRoomIdOrderByRecordedAtDesc(String roomId, Pageable pageable);
}
