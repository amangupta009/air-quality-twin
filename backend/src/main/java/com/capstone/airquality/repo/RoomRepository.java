package com.capstone.airquality.repo;

import com.capstone.airquality.domain.Room;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomRepository extends JpaRepository<Room, String> {
}
