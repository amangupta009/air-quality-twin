package com.capstone.airquality.sensor;

import com.capstone.airquality.config.MqttProperties;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;

/**
 * Small helper for PUBLISHING to MQTT (used by the built-in simulator).
 * Kept separate from MqttSensorDataSource so the listening side stays clean.
 */
@Component
public class MqttGateway {

    private static final Logger log = LoggerFactory.getLogger(MqttGateway.class);

    private final MqttProperties mqttProps;
    private MqttClient publisher;

    public MqttGateway(MqttProperties mqttProps) {
        this.mqttProps = mqttProps;
    }

    private synchronized MqttClient client() throws MqttException {
        if (publisher == null) {
            publisher = new MqttClient(mqttProps.brokerUri(), mqttProps.clientId() + "-pub",
                    new MemoryPersistence());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            publisher.connect(options);
        }
        return publisher;
    }

    public void publish(String topic, String jsonPayload) {
        try {
            MqttClient c = client();
            if (!c.isConnected()) {
                c.reconnect();
            }
            c.publish(topic, new MqttMessage(jsonPayload.getBytes(StandardCharsets.UTF_8)));
        } catch (MqttException e) {
            log.warn("Failed to publish to {}: {}", topic, e.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        if (publisher != null && publisher.isConnected()) {
            try {
                publisher.disconnect();
            } catch (MqttException ignored) {
            }
        }
    }
}
