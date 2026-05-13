/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import io.camunda.authentication.ConditionalOnAuthenticationMethod;
import io.camunda.authentication.converter.UsernamePasswordAuthenticationTokenConverter;
import io.camunda.security.api.context.CamundaAuthenticationConverter;
import io.camunda.security.api.model.config.AuthenticationConfiguration;
import io.camunda.security.api.model.config.AuthenticationMethod;
import io.camunda.security.api.model.config.oidc.OidcConfiguration;
import io.camunda.security.api.model.config.oidc.OidcProvidersConfiguration;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.service.GroupServices;
import io.camunda.service.RoleServices;
import io.camunda.service.TenantServices;
import io.camunda.spring.utils.ConditionalOnSecondaryStorageEnabled;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;

/**
 * Host-side basic-authentication bean overrides. Lifted from the previous {@code
 * WebSecurityConfig.BasicConfiguration} nested class. Provides the {@link
 * UsernamePasswordAuthenticationTokenConverter} and verifies that no OIDC configuration is present
 * when basic auth is selected.
 */
@Configuration
@ConditionalOnAuthenticationMethod(AuthenticationMethod.BASIC)
@ConditionalOnSecondaryStorageEnabled
public class BasicAuthBeansConfiguration {

  private final SecurityConfiguration securityConfiguration;

  public BasicAuthBeansConfiguration(final SecurityConfiguration securityConfiguration) {
    this.securityConfiguration = securityConfiguration;
  }

  @PostConstruct
  public void verifyBasicConfiguration() {
    if (isOidcConfigurationEnabled(securityConfiguration)) {
      throw new IllegalStateException(
          "Oidc configuration is not supported with `BASIC` authentication method");
    }
  }

  protected boolean isOidcConfigurationEnabled(final SecurityConfiguration securityConfiguration) {
    final var oidc = securityConfiguration.getAuthentication().getOidc();
    if (oidc != null && oidc.isAnyPropertySet()) {
      return true;
    }

    return Optional.ofNullable(securityConfiguration.getAuthentication())
        .map(AuthenticationConfiguration::getProviders)
        .map(OidcProvidersConfiguration::getOidc)
        .map(Map::values)
        .map(values -> values.stream().anyMatch(OidcConfiguration::isAnyPropertySet))
        .orElse(false);
  }

  @Bean
  public CamundaAuthenticationConverter<Authentication> usernamePasswordAuthenticationConverter(
      final RoleServices roleServices,
      final GroupServices groupServices,
      final TenantServices tenantServices) {
    return new UsernamePasswordAuthenticationTokenConverter(
        roleServices, groupServices, tenantServices);
  }
}
