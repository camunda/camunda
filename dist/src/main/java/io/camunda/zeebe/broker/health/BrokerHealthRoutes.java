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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.web.ManagementContextConfiguration;
import org.springframework.boot.actuate.autoconfigure.web.ManagementContextType;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Profile("broker")
@ConditionalOnManagementContext
@ManagementContextConfiguration(value = ManagementContextType.ANY, proxyBeanMethods = false)
public class BrokerHealthRoutes {

  private final ManagementServerProperties properties;

  @Autowired
  public BrokerHealthRoutes(final ManagementServerProperties properties) {
    this.properties = properties;
  }

  @Bean
  public RouterFunction<ServerResponse> routes() {
    final String basePath = properties.getBasePath();
    return RouterFunctions.route()
        .GET("/health", req -> movedPermanently(basePath + "/actuator/health/status"))
        .GET("/ready", req -> movedPermanently(basePath + "/actuator/health/readiness"))
        .GET("/startup", req -> movedPermanently(basePath + "/actuator/health/startup"))
        .build();
  }

  private Mono<ServerResponse> movedPermanently(final String path) {
    return ServerResponse.status(HttpStatus.MOVED_PERMANENTLY).location(URI.create(path)).build();
  }
}
