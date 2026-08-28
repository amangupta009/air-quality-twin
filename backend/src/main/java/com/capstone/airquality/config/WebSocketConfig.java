package com.capstone.airquality.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Live-push channel to the frontend (the "WebSocket" arrow in the data-flow
 * diagram). Frontend connects to /ws, then subscribes to /topic/rooms/{id}
 * to receive twin updates the moment a reading is ingested.
 *
 * STOMP is a tiny messaging protocol on top of WebSocket; Spring speaks it
 * natively, which saves us writing our own subscribe/unsubscribe protocol.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // allowedOriginPatterns "*" is fine for local dev; tighten for production.
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
    }
}
