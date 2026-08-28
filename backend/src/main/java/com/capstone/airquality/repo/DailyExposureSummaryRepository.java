package com.capstone.airquality.repo;

import com.capstone.airquality.domain.DailyExposureSummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyExposureSummaryRepository extends JpaRepository<DailyExposureSummary, Long> {

    Optional<DailyExposureSummary> findByRoomIdAndSummaryDate(String roomId, LocalDate date);

    List<DailyExposureSummary> findByRoomIdOrderBySummaryDateDesc(String roomId);
}
