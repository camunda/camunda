/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.spring.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.gatekeeper.config.AuthenticationConfig;
import io.camunda.gatekeeper.spi.CamundaAuthenticationConverter;
import io.camunda.gatekeeper.spi.CamundaAuthenticationHolder;
import io.camunda.gatekeeper.spi.CamundaAuthenticationProvider;
import io.camunda.gatekeeper.spring.DefaultCamundaAuthenticationProvider;
import io.camunda.gatekeeper.spring.config.GatekeeperProperties;
import io.camunda.gatekeeper.spring.converter.CamundaAuthenticationDelegatingConverter;
import io.camunda.gatekeeper.spring.handler.AuthFailureHandler;
import io.camunda.gatekeeper.spring.holder.CamundaAuthenticationDelegatingHolder;
import io.camunda.gatekeeper.spring.holder.HttpSessionBasedAuthenticationHolder;
import io.camunda.gatekeeper.spring.holder.RequestContextBasedAuthenticationHolder;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.Authentication;

/**
 * Core auto-configuration for the Gatekeeper SDK. Wires up the authentication provider, converter
 * chain, holder chain, and failure handler.
 */
@AutoConfiguration
@EnableConfigurationProperties(GatekeeperProperties.class)
public final class GatekeeperAuthAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public AuthenticationConfig authenticationConfig(final GatekeeperProperties properties) {
    return properties.toAuthenticationConfig();
  }

  @Bean
  @ConditionalOnMissingBean(name = "requestContextBasedAuthenticationHolder")
  @ConditionalOnWebApplication
  public CamundaAuthenticationHolder requestContextBasedAuthenticationHolder(
      final HttpServletRequest request) {
    return new RequestContextBasedAuthenticationHolder(request);
  }

  @Bean
  @ConditionalOnMissingBean(name = "httpSessionBasedAuthenticationHolder")
  @ConditionalOnWebApplication
  public CamundaAuthenticationHolder httpSessionBasedAuthenticationHolder(
      final HttpServletRequest request, final AuthenticationConfig authenticationConfig) {
    return new HttpSessionBasedAuthenticationHolder(request, authenticationConfig);
  }

  @Bean
  @ConditionalOnMissingBean
  public CamundaAuthenticationProvider camundaAuthenticationProvider(
      final List<CamundaAuthenticationHolder> holders,
      final List<CamundaAuthenticationConverter<Authentication>> converters) {
    return new DefaultCamundaAuthenticationProvider(
        new CamundaAuthenticationDelegatingHolder(holders),
        new CamundaAuthenticationDelegatingConverter(converters));
  }

  @Bean
  @ConditionalOnMissingBean
  public AuthFailureHandler authFailureHandler(final ObjectMapper objectMapper) {
    return new AuthFailureHandler(objectMapper);
  }
}
