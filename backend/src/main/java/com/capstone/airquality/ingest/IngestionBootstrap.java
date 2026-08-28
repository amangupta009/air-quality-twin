package com.capstone.airquality.ingest;

import com.capstone.airquality.sensor.SensorDataSource;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Connects the pieces at startup: "data source, send every reading you get
 * to the ingest service". This one line is where the abstraction layer pays
 * off - swapping simulator/hardware or plugging a fake source in tests never
 * touches this class.
 */
@Component
public class IngestionBootstrap implements ApplicationRunner {

    private final SensorDataSource dataSource;
    private final ReadingIngestService ingestService;

    public IngestionBootstrap(SensorDataSource dataSource, ReadingIngestService ingestService) {
        this.dataSource = dataSource;
        this.ingestService = ingestService;
    }

    @Override
    public void run(ApplicationArguments args) {
        dataSource.start(ingestService);
    }
}
