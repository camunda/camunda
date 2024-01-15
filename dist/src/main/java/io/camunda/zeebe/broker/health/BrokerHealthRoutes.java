/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.health;

import io.camunda.zeebe.shared.management.ConditionalOnManagementContext;
import java.net.URI;
import org.springframework.boot.actuate.autoconfigure.web.ManagementContextConfiguration;
import org.springframework.boot.actuate.autoconfigure.web.ManagementContextType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Profile("broker")
@ManagementContextConfiguration(value = ManagementContextType.ANY, proxyBeanMethods = false)
public class BrokerHealthRoutes {

  @Bean
  @ConditionalOnManagementContext
  public RouterFunction<ServerResponse> routes() {
    return RouterFunctions.route()
        .GET(
            "/health",
            req ->
                ServerResponse.permanentRedirect(URI.create("/actuator/health/liveness")).build())
        .GET(
            "/ready",
            req ->
                ServerResponse.permanentRedirect(URI.create("/actuator/health/readiness")).build())
        .GET(
            "/startup",
            req -> ServerResponse.permanentRedirect(URI.create("/actuator/health/startup")).build())
        .build();
  }
}
