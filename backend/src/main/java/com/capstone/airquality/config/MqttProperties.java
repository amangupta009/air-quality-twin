package com.capstone.airquality.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed view of the mqtt.* entries in application.properties. */
@ConfigurationProperties(prefix = "mqtt")
public record MqttProperties(
        String brokerUri,
        String clientId,
        String topicPrefix) {
}
