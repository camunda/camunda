/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.metrics;

import io.camunda.gateway.mcp.ConditionalOnMcpGatewayEnabled;
import io.micrometer.common.KeyValues;
import io.micrometer.observation.ObservationRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.function.Function;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.server.observation.DefaultServerRequestObservationConvention;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.http.server.observation.ServerRequestObservationConvention;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.ServerHttpObservationFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

@Configuration(proxyBeanMethods = false)
@ConditionalOnMcpGatewayEnabled
public class ServerRequestObservationConfiguration {

  @Bean
  public FilterRegistrationBean<ServerHttpObservationFilter> serverHttpObservationFilter(
      final ObservationRegistry observationRegistry,
      final ServerRequestObservationConvention customRequestObservationConvention) {
    // Match Spring Boot's WebMvcObservationAutoConfiguration behavior
    // but wrap the request in a ContentCachingRequestWrapper to read the body multiple times
    final var filter =
        new ServerHttpObservationFilter(observationRegistry, customRequestObservationConvention) {
          @Override
          protected void doFilterInternal(
              final @NonNull HttpServletRequest request,
              final @NonNull HttpServletResponse response,
              final @NonNull FilterChain filterChain)
              throws ServletException, IOException {
            super.doFilterInternal(
                new ContentCachingRequestWrapper(request), response, filterChain);
          }
        };

    final var registration = new FilterRegistrationBean<>(filter);
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
    registration.setName("serverHttpObservationFilter");
    return registration;
  }

  @Bean
  public ServerRequestObservationConvention customRequestObservationConvention(
      final ServerRequestLowCardinalityKeyValuesMapper lowCardinalityKeyValuesMapper) {
    // extend the default convention to add custom low cardinality key values
    return new DefaultServerRequestObservationConvention() {
      @Override
      public @NonNull KeyValues getLowCardinalityKeyValues(
          final @NonNull ServerRequestObservationContext context) {
        final var lowCardinalityKeyValues = super.getLowCardinalityKeyValues(context);
        final var newLowCardinalityKeyValues = lowCardinalityKeyValuesMapper.apply(context);
        return lowCardinalityKeyValues.and(newLowCardinalityKeyValues);
      }
    };
  }

  public interface ServerRequestLowCardinalityKeyValuesMapper
      extends Function<ServerRequestObservationContext, KeyValues> {}
}
