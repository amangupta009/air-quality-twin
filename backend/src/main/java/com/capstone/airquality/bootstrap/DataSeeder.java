package com.capstone.airquality.bootstrap;

import com.capstone.airquality.domain.Room;
import com.capstone.airquality.repo.RoomRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/** Makes the demo self-contained: room101 exists on first boot. */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final RoomRepository roomRepo;

    public DataSeeder(RoomRepository roomRepo) {
        this.roomRepo = roomRepo;
    }

    @Override
    public void run(String... args) {
        seedIfMissing(new Room("room101", "Lab 101", 8, 60.0));
    }

    private void seedIfMissing(Room room) {
        if (!roomRepo.existsById(room.getId())) {
            roomRepo.save(room);
            log.info("Seeded room {}", room.getId());
        }
    }
}
