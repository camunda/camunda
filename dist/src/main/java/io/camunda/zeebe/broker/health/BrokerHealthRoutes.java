/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.health;

import io.camunda.zeebe.shared.management.ConditionalOnManagementContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointProperties;
import org.springframework.boot.actuate.autoconfigure.web.ManagementContextConfiguration;
import org.springframework.boot.actuate.autoconfigure.web.ManagementContextType;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriBuilderFactory;

@Profile("broker")
@ConditionalOnManagementContext
@ManagementContextConfiguration(value = ManagementContextType.ANY, proxyBeanMethods = false)
public class BrokerHealthRoutes {

  private final UriBuilderFactory uriBuilderFactory = new DefaultUriBuilderFactory();

  private final ManagementServerProperties serverProperties;
  private final WebEndpointProperties actuatorProperties;

  @Autowired
  public BrokerHealthRoutes(
      final ManagementServerProperties serverProperties,
      final WebEndpointProperties actuatorProperties) {
    this.serverProperties = serverProperties;
    this.actuatorProperties = actuatorProperties;
  }

  @Bean
  public RouterFunction<ServerResponse> routes() {
    final var serverBasePath = serverProperties.getBasePath();
    final var actuatorBasePath = actuatorProperties.getBasePath();

    return RouterFunctions.route()
        .GET("/health", req -> movedPermanently(serverBasePath, actuatorBasePath, "/health/status"))
        .GET(
            "/ready",
            req -> movedPermanently(serverBasePath, actuatorBasePath, "/health/readiness"))
        .GET(
            "/startup",
            req -> movedPermanently(serverBasePath, actuatorBasePath, "/health/startup"))
        .build();
  }

  private ServerResponse movedPermanently(final String... paths) {
    final var builder = uriBuilderFactory.builder();
    for (final var path : paths) {
      builder.path(path);
    }

    return ServerResponse.status(HttpStatus.MOVED_PERMANENTLY).location(builder.build()).build();
  }
}
