package com.capstone.airquality.repo;

import com.capstone.airquality.domain.CalibrationProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CalibrationProfileRepository extends JpaRepository<CalibrationProfile, Long> {

    Optional<CalibrationProfile> findTopByRoomIdAndMetricOrderByCalibratedAtDesc(
            String roomId, String metric);
}
