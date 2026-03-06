/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.application.commons.authentication.CamundaNoDBMembershipResolver;
import io.camunda.auth.domain.config.AuthenticationConfiguration;
import io.camunda.auth.domain.config.OidcAuthenticationConfiguration;
import io.camunda.auth.domain.exception.BasicAuthenticationNotSupportedException;
import io.camunda.auth.domain.model.AuthenticationMethod;
import io.camunda.auth.spring.converter.TokenClaimsConverter;
import io.camunda.auth.starter.CamundaBasicAuthNoDbAutoConfiguration;
import io.camunda.security.configuration.SecurityConfiguration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
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
    // given - application context with basic auth and no secondary storage
    final var context = new AnnotationConfigApplicationContext();
    context.getEnvironment().getSystemProperties().put("camunda.auth.method", "basic");
    context.register(CamundaBasicAuthNoDbAutoConfiguration.class);

    // when/then - should fail fast with clear error message
    assertThatThrownBy(context::refresh)
        .isInstanceOf(BeanCreationException.class)
        .hasRootCauseInstanceOf(BasicAuthenticationNotSupportedException.class);
  }

  @Test
  void shouldAllowOidcAuthenticationInNoDbModeWithLimitedFunctionality() {
    // given - application context with no secondary storage and OIDC auth
    final var context = new AnnotationConfigApplicationContext();
    context.register(TestOidcAuthConfiguration.class);
    context.refresh();

    // then - should have the no-db OIDC service implementation
    final var tokenClaimsConverter = context.getBean(TokenClaimsConverter.class);
    assertThat(tokenClaimsConverter).isNotNull();

    // and - the service should work with limited functionality
    final Map<String, Object> claims =
        Map.of("preferred_username", "testuser", "groups", List.of("group1", "group2"));
    final var camundaAuthentication = tokenClaimsConverter.convert(claims);

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
    public CamundaNoDBMembershipResolver noDBMembershipResolver(
        final SecurityConfiguration securityConfiguration) {
      return new CamundaNoDBMembershipResolver(securityConfiguration);
    }

    @Bean
    public TokenClaimsConverter tokenClaimsConverter(
        final SecurityConfiguration securityConfiguration,
        final CamundaNoDBMembershipResolver noDBMembershipResolver) {
      final var oidcConfig = securityConfiguration.getAuthentication().getOidc();
      return new TokenClaimsConverter(
          oidcConfig.getUsernameClaim(),
          oidcConfig.getClientIdClaim(),
          oidcConfig.isPreferUsernameClaim(),
          noDBMembershipResolver);
    }
  }
}
