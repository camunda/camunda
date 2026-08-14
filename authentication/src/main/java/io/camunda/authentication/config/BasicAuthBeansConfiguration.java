/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import io.camunda.authentication.service.PhysicalTenantMembershipContextPropagator;
import io.camunda.security.api.context.CamundaAuthenticationConverter;
import io.camunda.security.api.context.MembershipResolutionContextPropagator;
import io.camunda.security.api.model.config.AuthenticationConfiguration;
import io.camunda.security.api.model.config.AuthenticationMethod;
import io.camunda.security.api.model.config.oidc.OidcConfiguration;
import io.camunda.security.api.model.config.oidc.OidcProvidersConfiguration;
import io.camunda.security.core.port.out.MembershipPort;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import io.camunda.security.spring.annotation.ConditionalOnAuthenticationMethod;
import io.camunda.security.spring.converter.LazyUsernamePasswordAuthenticationTokenConverter;
import io.camunda.spring.utils.ConditionalOnSecondaryStorageEnabled;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;

/**
 * Host-side basic-authentication bean overrides. Lifted from the previous {@code
 * WebSecurityConfig.BasicConfiguration} nested class. Provides the {@link
 * LazyUsernamePasswordAuthenticationTokenConverter} and verifies that no OIDC configuration is
 * present when basic auth is selected.
 */
@Configuration
@ConditionalOnAuthenticationMethod(AuthenticationMethod.BASIC)
@ConditionalOnSecondaryStorageEnabled
public class BasicAuthBeansConfiguration {

  private final CamundaSecurityLibraryProperties cslProperties;

  public BasicAuthBeansConfiguration(final CamundaSecurityLibraryProperties cslProperties) {
    this.cslProperties = cslProperties;
  }

  @PostConstruct
  public void verifyBasicConfiguration() {
    if (isOidcConfigurationEnabled(cslProperties)) {
      throw new IllegalStateException(
          "Oidc configuration is not supported with `BASIC` authentication method");
    }
  }

  protected boolean isOidcConfigurationEnabled(
      final CamundaSecurityLibraryProperties cslProperties) {
    final var oidc = cslProperties.getAuthentication().getOidc();
    if (oidc != null && oidc.isAnyPropertySet()) {
      return true;
    }

    return Optional.ofNullable(cslProperties.getAuthentication())
        .map(AuthenticationConfiguration::getProviders)
        .map(OidcProvidersConfiguration::getOidc)
        .map(Map::values)
        .map(values -> values.stream().anyMatch(OidcConfiguration::isAnyPropertySet))
        .orElse(false);
  }

  /**
   * Physical-tenant-aware propagator for the lazy membership lists built during BASIC-auth login,
   * so they still resolve once the request scope that built them is gone.
   *
   * <p>Not redundant with CSL's bean of the same type, which defaults to {@code identity()}: this
   * wins only because this configuration is a plain {@code @Import} while CSL arrives via the
   * deferred {@code @ImportAutoConfiguration}. Removing it silently restores the no-op.
   */
  @Bean
  @ConditionalOnMissingBean
  public MembershipResolutionContextPropagator membershipResolutionContextPropagator() {
    return new PhysicalTenantMembershipContextPropagator();
  }

  @Bean
  public CamundaAuthenticationConverter<Authentication> usernamePasswordAuthenticationConverter(
      final MembershipPort membershipPort,
      final MembershipResolutionContextPropagator membershipResolutionContextPropagator) {
    return new LazyUsernamePasswordAuthenticationTokenConverter(
        membershipPort, membershipResolutionContextPropagator);
  }
}
