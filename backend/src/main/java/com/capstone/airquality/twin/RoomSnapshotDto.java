package com.capstone.airquality.twin;

import java.time.Instant;

/**
 * Serializable view of one room's live twin state.
 * Shared by the WebSocket broadcast and the REST endpoints so both always
 * expose exactly the same shape to consumers.
 */
public record RoomSnapshotDto(
        String roomId,
        String roomName,
        Double co2Ppm,
        Integer occupants,
        boolean ventilationOn,
        String status,
        String recommendation,
        Instant updatedAt) {
}
