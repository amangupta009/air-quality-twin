package com.capstone.airquality.api;

import com.capstone.airquality.domain.DailyExposureSummary;
import com.capstone.airquality.repo.RoomRepository;
import com.capstone.airquality.summary.DailyExposureService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Module: daily exposure summary.
 * GET /api/summary/daily/{roomId}?date=YYYY-MM-DD  -> single day rollup
 * GET /api/summary/{roomId}                        -> recent summaries
 */
@RestController
@RequestMapping("/api/summary")
public class SummaryController {

    private final DailyExposureService summaryService;
    private final RoomRepository roomRepo;

    public SummaryController(DailyExposureService summaryService, RoomRepository roomRepo) {
        this.summaryService = summaryService;
        this.roomRepo = roomRepo;
    }

    @GetMapping("/daily/{roomId}")
    public ResponseEntity<DailyExposureSummary> daily(
            @PathVariable String roomId,
            @RequestParam(required = false) String date) {
        if (!roomRepo.existsById(roomId)) {
            return ResponseEntity.notFound().build();
        }
        LocalDate day = date != null ? LocalDate.parse(date) : LocalDate.now();
        return ResponseEntity.ok(summaryService.computeForDate(roomId, day));
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<List<DailyExposureSummary>> recent(@PathVariable String roomId) {
        if (!roomRepo.existsById(roomId)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(summaryService.recentRoomSummary(roomId));
    }
}
