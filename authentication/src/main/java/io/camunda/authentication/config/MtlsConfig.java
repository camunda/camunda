/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import io.camunda.authentication.converter.PreAuthenticatedAuthenticationTokenConverter;
import io.camunda.authentication.filters.MutualTlsAuthenticationFilter;
import io.camunda.authentication.providers.MutualTlsAuthenticationProvider;
import io.camunda.authentication.service.CertificateUserService;
import io.camunda.security.auth.CamundaAuthenticationConverter;
import io.camunda.service.GroupServices;
import io.camunda.service.RoleServices;
import io.camunda.service.TenantServices;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;

/**
 * Configuration class for mutual TLS (mTLS) authentication components. This configuration is only
 * loaded when mTLS authentication is enabled.
 */
@Configuration
@ConditionalOnMtlsEnabled
public class MtlsConfig {

  private static final Logger LOG = LoggerFactory.getLogger(MtlsConfig.class);

  @Bean
  public MutualTlsAuthenticationProvider mutualTlsAuthenticationProvider(
      final MutualTlsProperties mtlsProperties) {
    LOG.info(
        "Creating mTLS authentication provider with {} default roles",
        mtlsProperties.getDefaultRoles().size());
    return new MutualTlsAuthenticationProvider(mtlsProperties);
  }

  @Bean
  public MutualTlsAuthenticationFilter mutualTlsAuthenticationFilter(
      final MutualTlsAuthenticationProvider mutualTlsAuthenticationProvider,
      final Optional<CertificateUserService> certificateUserService) {
    LOG.info(
        "Creating mTLS authentication filter with user service: {}",
        certificateUserService.isPresent());
    return new MutualTlsAuthenticationFilter(
        mutualTlsAuthenticationProvider, certificateUserService.orElse(null));
  }

  @Bean
  public CamundaAuthenticationConverter<Authentication> preAuthenticatedAuthenticationConverter(
      final RoleServices roleServices,
      final GroupServices groupServices,
      final TenantServices tenantServices) {
    return new PreAuthenticatedAuthenticationTokenConverter(
        roleServices, groupServices, tenantServices);
  }
}
