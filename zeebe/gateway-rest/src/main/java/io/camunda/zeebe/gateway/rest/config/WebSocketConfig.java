/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.config;

import io.camunda.zeebe.gateway.rest.websocket.ProcessInstanceUpdateWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

  private final ProcessInstanceUpdateWebSocketHandler processInstanceUpdateHandler;

  @Autowired
  public WebSocketConfig(final ProcessInstanceUpdateWebSocketHandler processInstanceUpdateHandler) {
    this.processInstanceUpdateHandler = processInstanceUpdateHandler;
  }

  @Override
  public void registerWebSocketHandlers(final WebSocketHandlerRegistry registry) {
    registry
        .addHandler(processInstanceUpdateHandler, "/v2/process-instances/ws/{processInstanceKey}")
        .setAllowedOrigins("*");
  }
}
