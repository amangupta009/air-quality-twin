package com.capstone.airquality.sensor;

import com.capstone.airquality.config.MqttProperties;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Production SensorDataSource: subscribes to sensors/+/+ on the MQTT broker
 * and forwards every valid message into the pipeline as a SensorReading.
 *
 * This class works IDENTICALLY whether the publisher is our simulator or a
 * real ESP32 - that is what makes the hardware swap a one-property change.
 *
 * Robustness (an acceptance-gate requirement): if the broker is down at
 * startup we retry in the background instead of crashing the app, and Paho's
 * automaticReconnect handles drops after a successful connect. Malformed
 * messages are logged and skipped, never fatal.
 */
@Component
public class MqttSensorDataSource implements SensorDataSource {

    private static final Logger log = LoggerFactory.getLogger(MqttSensorDataSource.class);

    private final MqttProperties mqttProps;
    private final ObjectMapper objectMapper;

    private MqttClient client;
    private ScheduledExecutorService retryExecutor;
    private volatile boolean running;

    public MqttSensorDataSource(MqttProperties mqttProps, ObjectMapper objectMapper) {
        this.mqttProps = mqttProps;
        this.objectMapper = objectMapper;
    }

    @Override
    public void start(SensorReadingListener listener) {
        running = true;
        tryConnect(listener);
    }

    private void tryConnect(SensorReadingListener listener) {
        if (!running) {
            return;
        }
        try {
            client = new MqttClient(mqttProps.brokerUri(), mqttProps.clientId() + "-sub",
                    new MemoryPersistence());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            client.setCallback(new MqttCallbackExtended() {
                @Override
                public void connectComplete(boolean reconnect, String serverURI) {
                    // Re-issue the subscription on BOTH first connect and every
                    // auto-reconnect: with cleanSession=true the broker forgets
                    // our subscription when the link drops, so without this the
                    // backend silently stops getting readings after a reconnect.
                    try {
                        String filter = mqttProps.topicPrefix() + "/+/+";
                        client.subscribe(filter);
                        log.info("(Re)subscribed to {} on {}", filter, serverURI);
                    } catch (MqttException e) {
                        log.warn("Resubscribe failed: {}", e.getMessage());
                    }
                }

                @Override
                public void connectionLost(Throwable cause) {
                    log.warn("MQTT connection lost; automatic reconnect is active");
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    handleTopicMessage(topic, message, listener);
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    // not used on the subscribing client
                }
            });
            client.connect(options);
            // connectComplete() above re-issues the subscription on every
            // (re)connect, so no separate subscribe needed here.
            log.info("MQTT connected to {} (cleanSession={})",
                    mqttProps.brokerUri(), options.isCleanSession());
        } catch (MqttException e) {
            log.warn("MQTT broker not reachable yet ({}); retrying in 5s", e.getMessage());
            scheduleRetry(listener);
        }
    }

    private void scheduleRetry(SensorReadingListener listener) {
        if (retryExecutor == null) {
            retryExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "mqtt-retry");
                t.setDaemon(true);
                return t;
            });
        }
        retryExecutor.schedule(() -> tryConnect(listener), 5, TimeUnit.SECONDS);
    }

    /**
     * Topic layout: {prefix}/{roomId}/{metric} e.g. sensors/room101/co2
     * Payload JSON: {"value": 512.4, "unit": "ppm", "ts": "2026-08-21T10:00:00Z"}
     */
    private void handleTopicMessage(String topic, MqttMessage message, SensorReadingListener listener) {
        try {
            String[] parts = topic.split("/");
            if (parts.length != 3 || !parts[0].equals(mqttProps.topicPrefix())) {
                return;
            }
            String roomId = parts[1];
            String metric = parts[2];
            JsonNode payload = objectMapper.readTree(message.getPayload());
            double value = payload.get("value").asDouble();
            String unit = payload.has("unit") ? payload.get("unit").asText() : defaultUnitFor(metric);
            Instant ts = payload.has("ts")
                    ? Instant.parse(payload.get("ts").asText())
                    : Instant.now();
            listener.onReading(new SensorReading(roomId, metric, value, unit, ts));
        } catch (Exception e) {
            // Edge-case evidence: bad payloads are counted in logs, never crash ingestion.
            log.warn("Discarding malformed message on {}: {}", topic, e.getMessage());
        }
    }

    static String defaultUnitFor(String metric) {
        return switch (metric) {
            case "co2" -> "ppm";
            case "occupancy" -> "persons";
            default -> "raw";
        };
    }

    @Override
    @PreDestroy
    public void stop() {
        running = false;
        if (retryExecutor != null) {
            retryExecutor.shutdownNow();
        }
        if (client != null && client.isConnected()) {
            try {
                client.disconnect();
            } catch (MqttException e) {
                log.warn("Error while disconnecting MQTT client: {}", e.getMessage());
            }
        }
    }
}
