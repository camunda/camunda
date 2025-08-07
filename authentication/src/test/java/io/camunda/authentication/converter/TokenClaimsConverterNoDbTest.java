/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.authentication.service.MembershipService;
import io.camunda.authentication.service.NoDBMembershipService;
import io.camunda.security.configuration.AuthenticationConfiguration;
import io.camunda.security.configuration.OidcAuthenticationConfiguration;
import io.camunda.security.configuration.SecurityConfiguration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

public class TokenClaimsConverterNoDbTest {

  private TokenClaimsConverter tokenClaimsConverter;
  private MembershipService membershipService;
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

    membershipService = new NoDBMembershipService(securityConfiguration);
    tokenClaimsConverter = new TokenClaimsConverter(securityConfiguration, membershipService);
  }

  @Test
  void shouldLoadOAuthContextWithUsernameOnly() {
    // given
    final Map<String, Object> claims = Map.of("preferred_username", "testuser");

    // when
    final var result = tokenClaimsConverter.convert(claims);

    // then
    assertThat(result).isNotNull();
    assertThat(result.authenticatedUsername()).isEqualTo("testuser");
    assertThat(result.authenticatedClientId()).isNull();
    assertThat(result.authenticatedGroupIds()).isEmpty();
    assertThat(result.authenticatedRoleIds()).isEmpty();
    assertThat(result.authenticatedTenantIds()).isEmpty();
    assertThat(result.authenticatedMappingRuleIds()).isEmpty();
  }

  @Test
  void shouldLoadOAuthContextWithClientIdOnly() {
    // given
    final Map<String, Object> claims = Map.of("azp", "test-client");

    // when
    final var result = tokenClaimsConverter.convert(claims);

    // then
    assertThat(result).isNotNull();
    assertThat(result.authenticatedUsername()).isNull();
    assertThat(result.authenticatedClientId()).isEqualTo("test-client");
    assertThat(result.authenticatedGroupIds()).isEmpty();
    assertThat(result.authenticatedRoleIds()).isEmpty();
    assertThat(result.authenticatedTenantIds()).isEmpty();
    assertThat(result.authenticatedMappingRuleIds()).isEmpty();
  }

  @Test
  void shouldLoadOAuthContextWithUsernameAndGroups() {
    // given
    final Map<String, Object> claims =
        Map.of("preferred_username", "testuser", "groups", List.of("group1", "group2"));

    // when
    final var result = tokenClaimsConverter.convert(claims);

    // then
    assertThat(result).isNotNull();
    assertThat(result.authenticatedUsername()).isEqualTo("testuser");
    assertThat(result.authenticatedGroupIds()).containsExactlyInAnyOrder("group1", "group2");
    // In no-db mode, no secondary storage access, so these should be empty
    assertThat(result.authenticatedRoleIds()).isEmpty();
    assertThat(result.authenticatedTenantIds()).isEmpty();
    assertThat(result.authenticatedMappingRuleIds()).isEmpty();
  }

  @Test
  void shouldHandleEmptyGroupsClaim() {
    // given
    final Map<String, Object> claims =
        Map.of("preferred_username", "testuser", "groups", List.of());

    // when
    final var result = tokenClaimsConverter.convert(claims);

    // then
    assertThat(result).isNotNull();
    assertThat(result.authenticatedUsername()).isEqualTo("testuser");
    assertThat(result.authenticatedGroupIds()).isEmpty();
  }

  @Test
  void shouldThrowExceptionWhenNeitherUsernameNorClientIdPresent() {
    // given
    final Map<String, Object> claims = Map.of("some_other_claim", "value");

    // when & then
    assertThatThrownBy(() -> tokenClaimsConverter.convert(claims))
        .isInstanceOf(OAuth2AuthenticationException.class)
        .hasMessageContaining("Neither username claim")
        .hasMessageContaining("nor clientId claim");
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

    final var serviceNoGroups = new TokenClaimsConverter(config, membershipService);
    final Map<String, Object> claims = Map.of("preferred_username", "testuser");

    // when
    final var result = serviceNoGroups.convert(claims);

    // then
    assertThat(result).isNotNull();
    assertThat(result.authenticatedUsername()).isEqualTo("testuser");
    assertThat(result.authenticatedGroupIds()).isEmpty();
  }
}
