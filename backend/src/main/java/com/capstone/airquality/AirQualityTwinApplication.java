package com.capstone.airquality;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point of the whole backend.
 *
 * @EnableScheduling            -> lets the built-in simulator tick on a timer.
 * @ConfigurationPropertiesScan -> picks up our typed config classes
 *                                 (SensorProperties, MqttProperties, ...).
 */
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class AirQualityTwinApplication {

	public static void main(String[] args) {
		SpringApplication.run(AirQualityTwinApplication.class, args);
	}

}
