/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.authentication;

import io.camunda.application.commons.condition.ConditionalOnAnyHttpGatewayEnabled;
import io.camunda.authentication.ConditionalOnUnprotectedApi;
import io.camunda.authentication.converter.CamundaAuthenticationDelegatingConverter;
import io.camunda.authentication.holder.HttpSessionBasedAuthenticationHolder;
import io.camunda.security.api.context.CamundaAuthenticationConverter;
import io.camunda.security.api.context.CamundaAuthenticationHolder;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.core.context.holder.CamundaAuthenticationDelegatingHolder;
import io.camunda.security.spring.context.DefaultCamundaAuthenticationProvider;
import io.camunda.security.spring.context.holder.RequestContextBasedAuthenticationHolder;
import io.camunda.security.spring.converter.UnprotectedCamundaAuthenticationConverter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackages = {"io.camunda.service.validation"})
@ConditionalOnAnyHttpGatewayEnabled
public class CamundaAuthenticationConfiguration {

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
      final HttpServletRequest request, final SecurityConfiguration securityConfiguration) {
    return new HttpSessionBasedAuthenticationHolder(
        request, securityConfiguration.getAuthentication());
  }

  @Bean
  public CamundaAuthenticationProvider camundaAuthenticationProvider(
      final List<CamundaAuthenticationHolder> holders,
      final List<CamundaAuthenticationConverter<Authentication>> converters) {
    return new DefaultCamundaAuthenticationProvider(
        new CamundaAuthenticationDelegatingHolder(holders),
        new CamundaAuthenticationDelegatingConverter(converters));
  }
}
