package com.capstone.airquality.sensor;

import com.capstone.airquality.config.SensorProperties;
import com.capstone.airquality.domain.Room;
import com.capstone.airquality.occupancy.Co2Override;
import com.capstone.airquality.occupancy.OccupancyControl;
import com.capstone.airquality.repo.RoomRepository;
import com.capstone.airquality.twin.RoomTwinService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalTime;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The built-in simulator (active only when sensor.mode=simulator).
 *
 * Feeds directly into the ingestion pipeline (no MQTT roundtrip needed).
 * When sensor.mode=hardware the real ESP32 publishes to MQTT and
 * MqttSensorDataSource picks it up instead.
 *
 * Physics model:
 *   dCO2/dt = occupants * GEN - ACH/3600 * (CO2 - outdoor)
 *   - GEN ~ 0.045 ppm/s per person (respiration)
 *   - ACH = air changes per hour; low when ventilation OFF, high when ON.
 */
@Component
@ConditionalOnProperty(name = "sensor.mode", havingValue = "simulator")
public class SimulatorFeed {

    private static final Logger log = LoggerFactory.getLogger(SimulatorFeed.class);
    private static final double GEN_PPM_PER_PERSON_PER_SEC = 0.045;

    private final SensorProperties props;
    private final RoomTwinService twinService;
    private final RoomRepository roomRepo;
    private final OccupancyControl occupancyControl;
    private final Co2Override co2Override;
    private final SensorReadingListener listener;

    private static class SimState {
        double co2;
        int occupants;
        final Random rng = new Random();

        SimState(double startCo2) {
            this.co2 = startCo2;
        }
    }

    private final Map<String, SimState> states = new ConcurrentHashMap<>();

    public SimulatorFeed(SensorProperties props, RoomTwinService twinService,
                         RoomRepository roomRepo, OccupancyControl occupancyControl,
                         Co2Override co2Override, SensorReadingListener listener) {
        this.props = props;
        this.twinService = twinService;
        this.roomRepo = roomRepo;
        this.occupancyControl = occupancyControl;
        this.co2Override = co2Override;
        this.listener = listener;
    }

    @Scheduled(fixedRateString = "${sensor.simulator.interval-ms}")
    public void tick() {
        double dt = (props.simulator().intervalMs() / 1000.0) * props.simulator().timeFactor();
        Instant now = Instant.now();
        for (Room room : roomRepo.findAll()) {
            SimState s = states.computeIfAbsent(room.getId(),
                    id -> new SimState(props.simulator().outdoorCo2Ppm() + 20));
            stepOccupancy(s, room.getCapacity(), room.getId());
            stepCo2(s, dt, room.getId());
            listener.onReading(new SensorReading(room.getId(), "co2", round1(s.co2), "ppm", now));
            listener.onReading(new SensorReading(room.getId(), "occupancy", s.occupants, "persons", now));
        }
    }

    private void stepOccupancy(SimState s, int capacity, String roomId) {
        Integer manual = occupancyControl.get(roomId);
        if (manual != null) {
            s.occupants = Math.max(0, manual);
            return;
        }
        LocalTime now = LocalTime.now();
        boolean workHours = !now.isBefore(LocalTime.of(8, 0)) && now.isBefore(LocalTime.of(18, 0));
        int target = workHours ? Math.round(capacity * 0.75f) : 0;
        if (s.occupants < target && s.rng.nextDouble() < 0.5) {
            s.occupants++;
        } else if (s.occupants > target && s.rng.nextDouble() < 0.3) {
            s.occupants--;
        }
        if (s.rng.nextDouble() < 0.05) {
            s.occupants = Math.max(0, Math.min(capacity, s.occupants + (s.rng.nextBoolean() ? 1 : -1)));
        }
    }

    private void stepCo2(SimState s, double dt, String roomId) {
        Double manualCo2 = co2Override.consume(roomId);
        if (manualCo2 != null) {
            s.co2 = manualCo2;
            return;
        }

        boolean ventOn = twinService.isVentilationOn(roomId);
        double ach;
        if (ventOn) {
            double achNeededForComfort =
                    (s.occupants * GEN_PPM_PER_PERSON_PER_SEC * 3600.0) / 350.0;
            ach = Math.max(props.simulator().ventilationAch(), achNeededForComfort);
        } else {
            ach = props.simulator().infiltrationAch();
        }
        double decayPerSec = ach / 3600.0;
        double outdoor = props.simulator().outdoorCo2Ppm();
        s.co2 += (s.occupants * GEN_PPM_PER_PERSON_PER_SEC - decayPerSec * (s.co2 - outdoor)) * dt;
        s.co2 += s.rng.nextGaussian() * 2.0;
        s.co2 = Math.max(outdoor - 10, s.co2);
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
