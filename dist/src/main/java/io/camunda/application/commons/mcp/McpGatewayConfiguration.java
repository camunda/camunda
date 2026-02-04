/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.gateway.mcp.ConditionalOnMcpGatewayEnabled;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.boot.actuate.autoconfigure.observation.ObservationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.server.observation.ServerRequestObservationConvention;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackages = {"io.camunda.gateway.mcp"})
@ConditionalOnMcpGatewayEnabled
public class McpGatewayConfiguration {

  @Bean
  public ServerRequestObservationConvention mcpRequestObservationConvention(
      final ObservationProperties observationProperties, final ObjectMapper objectMapper) {
    return new McpServerRequestObservationConvention(
        observationProperties.getHttp().getServer().getRequests().getName(), objectMapper);
  }

  @Bean
  public FilterRegistrationBean<OncePerRequestFilter> mcpContentCachingFilter() {
    final FilterRegistrationBean<OncePerRequestFilter> registrationBean =
        new FilterRegistrationBean<>();

    registrationBean.setFilter(
        new OncePerRequestFilter() {
          @Override
          protected void doFilterInternal(
              final @NonNull HttpServletRequest request,
              final @NonNull HttpServletResponse response,
              final @NonNull FilterChain filterChain)
              throws ServletException, IOException {
            filterChain.doFilter(new ContentCachingRequestWrapper(request), response);
          }
        });
    registrationBean.addUrlPatterns("/mcp/*");
    registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE);

    return registrationBean;
  }
}
