/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rest;

import io.camunda.application.commons.rest.RestApiConfiguration.GatewayRestProperties;
import io.camunda.authentication.ConditionalOnUnprotectedApi;
import io.camunda.authentication.DefaultCamundaAuthenticationProvider;
import io.camunda.authentication.converter.CamundaAuthenticationDelegatingConverter;
import io.camunda.authentication.converter.UnprotectedCamundaAuthenticationConverter;
import io.camunda.authentication.holder.CamundaAuthenticationDelegatingHolder;
import io.camunda.authentication.holder.HttpSessionBasedAuthenticationHolder;
import io.camunda.authentication.holder.RequestContextBasedAuthenticationHolder;
import io.camunda.security.auth.CamundaAuthenticationConverter;
import io.camunda.security.auth.CamundaAuthenticationHolder;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.zeebe.gateway.rest.ConditionalOnRestGatewayEnabled;
import io.camunda.zeebe.gateway.rest.config.GatewayRestConfiguration;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackages = {"io.camunda.zeebe.gateway.rest", "io.camunda.service.validation"})
@ConditionalOnRestGatewayEnabled
@EnableConfigurationProperties(GatewayRestProperties.class)
public class RestApiConfiguration {

  @Bean
  @ConditionalOnUnprotectedApi
  public CamundaAuthenticationConverter<Authentication> unprotectedAuthenticationConverter() {
    return new UnprotectedCamundaAuthenticationConverter();
  }

  @Bean
  public CamundaAuthenticationHolder requestContextBasedAuthenticationHolder(
      final HttpServletRequest request) {
    return new RequestContextBasedAuthenticationHolder(request);
  }

  @Bean
  public CamundaAuthenticationHolder httpSessionBasedAuthenticationHolder(
      final HttpServletRequest request) {
    return new HttpSessionBasedAuthenticationHolder(request);
  }

  @Bean
  public CamundaAuthenticationProvider camundaAuthenticationProvider(
      final List<CamundaAuthenticationHolder> holders,
      final List<CamundaAuthenticationConverter<Authentication>> converters) {
    return new DefaultCamundaAuthenticationProvider(
        new CamundaAuthenticationDelegatingHolder(holders),
        new CamundaAuthenticationDelegatingConverter(converters));
  }

  @ConfigurationProperties("camunda.rest")
  public static final class GatewayRestProperties extends GatewayRestConfiguration {}
}
