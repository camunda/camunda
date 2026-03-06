/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.authentication;

import io.camunda.application.commons.condition.ConditionalOnAnyHttpGatewayEnabled;
import io.camunda.auth.domain.spi.CamundaAuthenticationConverter;
import io.camunda.auth.domain.spi.CamundaAuthenticationHolder;
import io.camunda.auth.domain.spi.CamundaAuthenticationProvider;
import io.camunda.auth.domain.support.CamundaAuthenticationDelegatingConverter;
import io.camunda.auth.domain.support.CamundaAuthenticationDelegatingHolder;
import io.camunda.auth.spring.DefaultCamundaAuthenticationProvider;
import io.camunda.auth.spring.converter.UnprotectedCamundaAuthenticationConverter;
import io.camunda.auth.spring.holder.HttpSessionBasedAuthenticationHolder;
import io.camunda.auth.spring.holder.RequestContextBasedAuthenticationHolder;
import io.camunda.security.configuration.SecurityConfiguration;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackages = {"io.camunda.service.validation"})
@ConditionalOnAnyHttpGatewayEnabled
public class CamundaAuthenticationConfiguration {

  @Bean
  @ConditionalOnProperty(
      name = "camunda.security.authentication.unprotected-api",
      havingValue = "true")
  public CamundaAuthenticationConverter<Authentication> unprotectedAuthenticationConverter() {
    return new UnprotectedCamundaAuthenticationConverter();
  }

  @Bean
  public CamundaAuthenticationHolder requestContextBasedAuthenticationHolder() {
    return new RequestContextBasedAuthenticationHolder();
  }

  @Bean
  public CamundaAuthenticationHolder httpSessionBasedAuthenticationHolder(
      final SecurityConfiguration securityConfiguration) {
    return new HttpSessionBasedAuthenticationHolder(
        Duration.parse(
            securityConfiguration.getAuthentication().getAuthenticationRefreshInterval()));
  }

  @Bean
  public CamundaAuthenticationProvider camundaAuthenticationProvider(
      final List<CamundaAuthenticationHolder> holders,
      final List<CamundaAuthenticationConverter<Authentication>> converters) {
    return new DefaultCamundaAuthenticationProvider(
        new CamundaAuthenticationDelegatingHolder(holders),
        new CamundaAuthenticationDelegatingConverter<>(converters));
  }
}
