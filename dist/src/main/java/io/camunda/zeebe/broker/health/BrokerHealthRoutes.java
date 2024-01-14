/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.health;

import io.camunda.zeebe.shared.management.ConditionalOnManagementContext;
import java.util.Map;
import org.springframework.boot.actuate.autoconfigure.web.ManagementContextConfiguration;
import org.springframework.boot.actuate.autoconfigure.web.ManagementContextType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Profile("broker")
@ManagementContextConfiguration(value = ManagementContextType.ANY, proxyBeanMethods = false)
public class BrokerHealthRoutes {

  private static final Map<String, String> HEALTH_REDIRECTS =
      Map.of(
          "/health",
          "/actuator/health/liveness",
          "/ready",
          "/actuator/health/readiness",
          "/startup",
          "/actuator/health/startup");

  private final WebFilter redirects;

  public BrokerHealthRoutes() {
    this(new CustomRedirect(HEALTH_REDIRECTS));
  }

  public BrokerHealthRoutes(final WebFilter redirects) {
    this.redirects = redirects;
  }

  @Bean
  @ConditionalOnManagementContext
  public WebFilter healthRedirects() {
    return redirects;
  }

  @SuppressWarnings("ClassCanBeRecord")
  private static final class CustomRedirect implements WebFilter {
    private final Map<String, String> redirects;

    private CustomRedirect(final Map<String, String> redirects) {
      this.redirects = redirects;
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public Mono<Void> filter(
        final ServerWebExchange exchange, final WebFilterChain webFilterChain) {
      final var request = exchange.getRequest();
      final var path = request.getPath().value();
      final var redirect = redirects.get(path);
      if (redirect != null) {
        final var redirected = exchange.mutate().request(builder -> builder.path(redirect)).build();
        return webFilterChain.filter(redirected);
      }

      return webFilterChain.filter(exchange);
    }
  }
}
