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
import io.camunda.gateway.mcp.config.CamundaMcpToolScannerAutoConfiguration;
import io.camunda.gateway.mcp.config.CamundaMcpToolSpecificationsAutoConfiguration;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.boot.micrometer.observation.autoconfigure.ObservationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.server.observation.ServerRequestObservationConvention;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

/**
 * Configuration for the MCP gateway implemented in the {@code gateway-mcp} module.
 *
 * <p>MCP specific beans overriding the default Spring AI behavior are created as part of the module
 * autoconfigurations in the {@link io.camunda.gateway.mcp.config} package to resemble the
 * overridden autoconfiguration logic as much as possible. See:
 *
 * <ul>
 *   <li>{@link CamundaMcpToolScannerAutoConfiguration}
 *   <li>{@link CamundaMcpToolSpecificationsAutoConfiguration}
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackages = {"io.camunda.gateway.mcp"})
@ConditionalOnMcpGatewayEnabled
public class McpGatewayConfiguration {

  /**
   * Custom ServerRequestObservationConvention for <bold>all</bold> server requests. This customizes
   * metrics for MCP requests only. Other requests are not affected but still processed by this
   * convention. If we want to extend or modify the default behavior for other requests, too, we
   * should create a more generic implementation.
   */
  @Bean
  public ServerRequestObservationConvention mcpRequestObservationConvention(
      final ObservationProperties observationProperties, final ObjectMapper objectMapper) {
    return new McpServerRequestObservationConvention(
        observationProperties.getHttp().getServer().getRequests().getName(), objectMapper);
  }

  /**
   * Filter that wraps incoming MCP requests in a ContentCachingRequestWrapper to allow multiple
   * reads of the request body. This is necessary because metrics observation and downstream
   * processing read the request body multiple times.
   */
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
            filterChain.doFilter(new ContentCachingRequestWrapper(request, 0), response);
          }
        });
    registrationBean.addUrlPatterns("/mcp/*");
    registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE);

    return registrationBean;
  }
}
