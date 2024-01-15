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
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Profile("broker")
@ManagementContextConfiguration(value = ManagementContextType.ANY, proxyBeanMethods = false)
public class BrokerHealthRoutes {

  @Bean
  @ConditionalOnManagementContext
  public RouterFunction<ServerResponse> routes() {
    return RouterFunctions.route()
        .GET("/health", req -> movedPermanently("/actuator/health/liveness"))
        .GET("/ready", req -> movedPermanently("/actuator/health/readiness"))
        .GET("/startup", req -> movedPermanently("/actuator/health/startup"))
        .build();
  }

  private Mono<ServerResponse> movedPermanently(final String path) {
    return ServerResponse.status(HttpStatus.MOVED_PERMANENTLY).location(URI.create(path)).build();
  }
}
