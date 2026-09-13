package com.capstone.airquality.api;

import com.capstone.airquality.domain.Room;
import com.capstone.airquality.repo.RoomRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public (no-auth) metadata endpoints used by the login screen, e.g. the
 * list of rooms that already exist so a user can pick one or type a new one.
 */
@RestController
@RequestMapping("/api/meta")
public class MetaController {

    private final RoomRepository roomRepo;

    public MetaController(RoomRepository roomRepo) {
        this.roomRepo = roomRepo;
    }

    @GetMapping("/rooms")
    public List<MetaRoom> rooms() {
        return roomRepo.findAll().stream()
                .map(r -> new MetaRoom(r.getId(), r.getName()))
                .toList();
    }

    public record MetaRoom(String id, String name) {
    }
}
