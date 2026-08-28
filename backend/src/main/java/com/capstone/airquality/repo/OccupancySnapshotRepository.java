package com.capstone.airquality.repo;

import com.capstone.airquality.domain.OccupancySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface OccupancySnapshotRepository extends JpaRepository<OccupancySnapshot, Long> {

    List<OccupancySnapshot> findByRoomIdAndRecordedAtBetweenOrderByRecordedAtAsc(
            String roomId, Instant from, Instant to);
}
