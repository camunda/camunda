/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.security;

import static io.camunda.spring.utils.DatabaseTypeUtils.CAMUNDA_DATABASE_TYPE_NONE;
import static io.camunda.spring.utils.DatabaseTypeUtils.PROPERTY_CAMUNDA_DATABASE_TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.authentication.config.WebSecurityConfig;
import io.camunda.authentication.converter.TokenClaimsConverter;
import io.camunda.authentication.exception.BasicAuthenticationNotSupportedException;
import io.camunda.authentication.service.NoDBMembershipService;
import io.camunda.security.configuration.AuthenticationConfiguration;
import io.camunda.security.configuration.OidcAuthenticationConfiguration;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.entity.AuthenticationMethod;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanInstantiationException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Integration test for authentication behavior in no-database mode. This test validates that the
 * authentication system properly handles the no-db scenario with appropriate fail-fast behavior and
 * limited functionality.
 */
public class NoSecondaryStorageAuthenticationIT {

  @Test
  void shouldFailFastWhenBasicAuthenticationConfiguredInNoDbMode() {
    // given - application context with no secondary storage and basic auth
    final var context = new AnnotationConfigApplicationContext();
    context
        .getEnvironment()
        .getSystemProperties()
        .put(PROPERTY_CAMUNDA_DATABASE_TYPE, CAMUNDA_DATABASE_TYPE_NONE);
    context
        .getEnvironment()
        .getSystemProperties()
        .put("camunda.security.authentication.method", "basic");

    // when - trying to start application with basic auth in no-db mode
    context.register(WebSecurityConfig.BasicAuthenticationNoDbConfiguration.class);

    // then - should fail fast with clear error message
    assertThatThrownBy(context::refresh)
        .isInstanceOf(BeanCreationException.class)
        .hasCauseInstanceOf(BeanInstantiationException.class)
        .hasRootCauseInstanceOf(BasicAuthenticationNotSupportedException.class);
  }

  @Test
  void shouldAllowOidcAuthenticationInNoDbModeWithLimitedFunctionality() {
    // given - application context with no secondary storage and OIDC auth
    final var context = new AnnotationConfigApplicationContext();
    context.getEnvironment().getSystemProperties().put(PROPERTY_CAMUNDA_DATABASE_TYPE, "none");
    context
        .getEnvironment()
        .getSystemProperties()
        .put("camunda.security.authentication.method", "oidc");

    // when - starting application with OIDC auth in no-db mode
    context.register(TestOidcAuthConfiguration.class);
    context.refresh();

    // then - should have the no-db OIDC service implementation
    final var oidcService = context.getBean(TokenClaimsConverter.class);
    assertThat(oidcService).isNotNull();

    // and - the service should work with limited functionality
    final Map<String, Object> claims =
        Map.of("preferred_username", "testuser", "groups", List.of("group1", "group2"));
    final var camundaAuthentication = oidcService.convert(claims);

    assertThat(camundaAuthentication.authenticatedUsername()).isEqualTo("testuser");
    assertThat(camundaAuthentication.authenticatedGroupIds())
        .containsExactlyInAnyOrder("group1", "group2");
    // No secondary storage access, so these should be empty
    assertThat(camundaAuthentication.authenticatedRoleIds()).isEmpty();
    assertThat(camundaAuthentication.authenticatedTenantIds()).isEmpty();
    assertThat(camundaAuthentication.authenticatedMappingRuleIds()).isEmpty();

    context.close();
  }

  @Configuration
  static class TestOidcAuthConfiguration {
    @Bean
    public SecurityConfiguration securityConfiguration() {
      final var config = new SecurityConfiguration();
      final var authConfig = new AuthenticationConfiguration();
      final var oidcConfig = new OidcAuthenticationConfiguration();
      oidcConfig.setUsernameClaim("preferred_username");
      oidcConfig.setClientIdClaim("azp");
      oidcConfig.setGroupsClaim("groups");
      authConfig.setOidc(oidcConfig);
      authConfig.setMethod(AuthenticationMethod.OIDC);
      config.setAuthentication(authConfig);
      return config;
    }

    @Bean
    public NoDBMembershipService noDBMembershipService(
        final SecurityConfiguration securityConfiguration) {
      return new NoDBMembershipService(securityConfiguration);
    }

    @Bean
    public TokenClaimsConverter camundaOAuthPrincipalServiceNoDb(
        final SecurityConfiguration securityConfiguration,
        final NoDBMembershipService noDBMembershipService) {
      return new TokenClaimsConverter(securityConfiguration, noDBMembershipService);
    }
  }
}
