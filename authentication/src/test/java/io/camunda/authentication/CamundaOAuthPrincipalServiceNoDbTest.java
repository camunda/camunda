/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.authentication.entity.OAuthContext;
import io.camunda.security.configuration.AuthenticationConfiguration;
import io.camunda.security.configuration.OidcAuthenticationConfiguration;
import io.camunda.security.configuration.SecurityConfiguration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

public class CamundaOAuthPrincipalServiceNoDbTest {

  private CamundaOAuthPrincipalServiceNoDbImpl service;
  private SecurityConfiguration securityConfiguration;

  @BeforeEach
  void setUp() {
    securityConfiguration = new SecurityConfiguration();
    final var authConfig = new AuthenticationConfiguration();
    final var oidcConfig = new OidcAuthenticationConfiguration();
    oidcConfig.setUsernameClaim("preferred_username");
    oidcConfig.setClientIdClaim("azp");
    oidcConfig.setGroupsClaim("groups");
    authConfig.setOidc(oidcConfig);
    securityConfiguration.setAuthentication(authConfig);

    service = new CamundaOAuthPrincipalServiceNoDbImpl(securityConfiguration);
  }

  @Test
  void shouldLoadOAuthContextWithUsernameOnly() {
    // given
    final Map<String, Object> claims = Map.of("preferred_username", "testuser");

    // when
    final OAuthContext result = service.loadOAuthContext(claims);

    // then
    assertThat(result).isNotNull();
    assertThat(result.authenticationContext().username()).isEqualTo("testuser");
    assertThat(result.authenticationContext().clientId()).isNull();
    assertThat(result.authenticationContext().groups()).isEmpty();
    assertThat(result.authenticationContext().roles()).isEmpty();
    assertThat(result.authenticationContext().tenants()).isEmpty();
    assertThat(result.authenticationContext().authorizedApplications()).isEmpty();
    assertThat(result.mappingIds()).isEmpty();
  }

  @Test
  void shouldLoadOAuthContextWithClientIdOnly() {
    // given
    final Map<String, Object> claims = Map.of("azp", "test-client");

    // when
    final OAuthContext result = service.loadOAuthContext(claims);

    // then
    assertThat(result).isNotNull();
    assertThat(result.authenticationContext().username()).isNull();
    assertThat(result.authenticationContext().clientId()).isEqualTo("test-client");
    assertThat(result.authenticationContext().groups()).isEmpty();
    assertThat(result.authenticationContext().roles()).isEmpty();
    assertThat(result.authenticationContext().tenants()).isEmpty();
    assertThat(result.authenticationContext().authorizedApplications()).isEmpty();
    assertThat(result.mappingIds()).isEmpty();
  }

  @Test
  void shouldLoadOAuthContextWithUsernameAndGroups() {
    // given
    final Map<String, Object> claims =
        Map.of("preferred_username", "testuser", "groups", List.of("group1", "group2"));

    // when
    final OAuthContext result = service.loadOAuthContext(claims);

    // then
    assertThat(result).isNotNull();
    assertThat(result.authenticationContext().username()).isEqualTo("testuser");
    assertThat(result.authenticationContext().groups())
        .containsExactlyInAnyOrder("group1", "group2");
    assertThat(result.authenticationContext().groupsClaimEnabled()).isTrue();
    // In no-db mode, no secondary storage access, so these should be empty
    assertThat(result.authenticationContext().roles()).isEmpty();
    assertThat(result.authenticationContext().tenants()).isEmpty();
    assertThat(result.authenticationContext().authorizedApplications()).isEmpty();
    assertThat(result.mappingIds()).isEmpty();
  }

  @Test
  void shouldHandleEmptyGroupsClaim() {
    // given
    final Map<String, Object> claims =
        Map.of("preferred_username", "testuser", "groups", List.of());

    // when
    final OAuthContext result = service.loadOAuthContext(claims);

    // then
    assertThat(result).isNotNull();
    assertThat(result.authenticationContext().username()).isEqualTo("testuser");
    assertThat(result.authenticationContext().groups()).isEmpty();
    assertThat(result.authenticationContext().groupsClaimEnabled()).isTrue();
  }

  @Test
  void shouldThrowExceptionWhenNeitherUsernameNorClientIdPresent() {
    // given
    final Map<String, Object> claims = Map.of("some_other_claim", "value");

    // when & then
    final OAuth2AuthenticationException exception =
        assertThrows(OAuth2AuthenticationException.class, () -> service.loadOAuthContext(claims));

    assertThat(exception.getError().getErrorCode()).isEqualTo("invalid_client");
    assertThat(exception.getMessage()).contains("Neither username claim");
    assertThat(exception.getMessage()).contains("nor clientId claim");
  }

  @Test
  void shouldHandleNoGroupsClaimConfiguration() {
    // given - service with no groups claim configured
    final var oidcConfig = new OidcAuthenticationConfiguration();
    oidcConfig.setUsernameClaim("preferred_username");
    oidcConfig.setClientIdClaim("azp");
    // No groupsClaim set
    final var authConfig = new AuthenticationConfiguration();
    authConfig.setOidc(oidcConfig);
    final var config = new SecurityConfiguration();
    config.setAuthentication(authConfig);

    final var serviceNoGroups = new CamundaOAuthPrincipalServiceNoDbImpl(config);
    final Map<String, Object> claims = Map.of("preferred_username", "testuser");

    // when
    final OAuthContext result = serviceNoGroups.loadOAuthContext(claims);

    // then
    assertThat(result).isNotNull();
    assertThat(result.authenticationContext().username()).isEqualTo("testuser");
    assertThat(result.authenticationContext().groups()).isEmpty();
    assertThat(result.authenticationContext().groupsClaimEnabled()).isFalse();
  }
}
