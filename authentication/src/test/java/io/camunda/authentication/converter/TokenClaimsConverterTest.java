/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.Mockito.when;

import io.camunda.authentication.service.MembershipService;
import io.camunda.authentication.service.NoDBMembershipService;
import io.camunda.security.configuration.AuthenticationConfiguration;
import io.camunda.security.configuration.OidcAuthenticationConfiguration;
import io.camunda.security.configuration.SecurityConfiguration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

@TestInstance(Lifecycle.PER_CLASS)
public class TokenClaimsConverterTest {

  private static final String USERNAME_CLAIM = "email";
  private static final String APPLICATION_ID_CLAIM = "client-id";
  private TokenClaimsConverter converter;
  private MembershipService membershipService;

  @Nested
  class ClientIdClaimConfiguration {

    @Mock private SecurityConfiguration securityConfiguration;
    @Mock private AuthenticationConfiguration authenticationConfiguration;
    @Mock private OidcAuthenticationConfiguration oidcAuthenticationConfiguration;

    @BeforeEach
    public void setUp() throws Exception {
      MockitoAnnotations.openMocks(this).close();

      when(securityConfiguration.getAuthentication()).thenReturn(authenticationConfiguration);
      when(authenticationConfiguration.getOidc()).thenReturn(oidcAuthenticationConfiguration);
      when(oidcAuthenticationConfiguration.getUsernameClaim()).thenReturn(USERNAME_CLAIM);
      when(oidcAuthenticationConfiguration.getClientIdClaim()).thenReturn(APPLICATION_ID_CLAIM);

      membershipService = new NoDBMembershipService(securityConfiguration);

      converter = new TokenClaimsConverter(securityConfiguration, membershipService);
    }

    @Test
    public void shouldThrowExceptionWhenNoClientIdClaimFound() {
      // given
      final Map<String, Object> claims = Map.of("sub", "user@example.com");

      // when
      final var exception =
          assertThatExceptionOfType(OAuth2AuthenticationException.class)
              .isThrownBy(() -> converter.convert(claims))
              .actual();

      assertThat(exception.getMessage())
          .isEqualTo(
              "Neither username claim (%s) nor clientId claim (%s) could be found in the claims. Please check your OIDC configuration."
                  .formatted(USERNAME_CLAIM, APPLICATION_ID_CLAIM));
    }

    @Test
    public void shouldThrowExceptionWhenClientIdClaimIsNotAString() {
      when(oidcAuthenticationConfiguration.getUsernameClaim()).thenReturn("not-tested");
      when(oidcAuthenticationConfiguration.getClientIdClaim()).thenReturn(APPLICATION_ID_CLAIM);
      // given
      final Map<String, Object> claims = Map.of(APPLICATION_ID_CLAIM, List.of("app-1", "app-2"));

      // when
      final var exception =
          assertThatExceptionOfType(IllegalArgumentException.class)
              .isThrownBy(() -> converter.convert(claims))
              .actual();

      assertThat(exception.getMessage())
          .isEqualTo(
              "Value for $['client-id'] is not a string. Please check your OIDC configuration.");
    }

    @Test
    public void shouldLoadUserWhenUsingClientIdClaim() {
      // given
      final Map<String, Object> claims =
          Map.of("sub", UUID.randomUUID().toString(), APPLICATION_ID_CLAIM, "app-1");

      // when
      final var camundaAuthentication = converter.convert(claims);

      // then
      assertThat(camundaAuthentication).isNotNull();
      assertThat(camundaAuthentication.authenticatedClientId()).isEqualTo("app-1");
      assertThat(camundaAuthentication.claims()).isEqualTo(claims);
    }

    @Test
    public void
        shouldExtractClientIdWhenPreferUsernameClaimIsFalseAndBothUsernameClaimAndClientIdClaimArePresent() {
      final Map<String, Object> claims =
          Map.of(USERNAME_CLAIM, "skipped", APPLICATION_ID_CLAIM, "preferred");

      final var camundaAuthentication = converter.convert(claims);

      assertThat(camundaAuthentication).isNotNull();
      assertThat(camundaAuthentication.authenticatedClientId()).isEqualTo("preferred");
      assertThat(camundaAuthentication.authenticatedUsername()).isNull();
    }

    @Test
    public void shouldExtractUsernameWhenPreferUsernameClaimIsFalseAndClientIdIsNotPresent() {
      final Map<String, Object> claims =
          Map.of(USERNAME_CLAIM, "my-user", "differentClaim", "preferred");

      final var camundaAuthentication = converter.convert(claims);

      assertThat(camundaAuthentication).isNotNull();
      assertThat(camundaAuthentication.authenticatedClientId()).isNull();
      assertThat(camundaAuthentication.authenticatedUsername()).isEqualTo("my-user");
    }
  }

  @Nested
  class UsernameClaimConfiguration {

    @Mock private SecurityConfiguration securityConfiguration;
    @Mock private AuthenticationConfiguration authenticationConfiguration;
    @Mock private OidcAuthenticationConfiguration oidcAuthenticationConfiguration;

    @BeforeEach
    public void setUp() throws Exception {
      MockitoAnnotations.openMocks(this).close();

      when(securityConfiguration.getAuthentication()).thenReturn(authenticationConfiguration);
      when(authenticationConfiguration.getOidc()).thenReturn(oidcAuthenticationConfiguration);
      when(oidcAuthenticationConfiguration.getUsernameClaim()).thenReturn(USERNAME_CLAIM);
      when(oidcAuthenticationConfiguration.getClientIdClaim()).thenReturn(APPLICATION_ID_CLAIM);

      membershipService = new NoDBMembershipService(securityConfiguration);

      converter = new TokenClaimsConverter(securityConfiguration, membershipService);
    }

    @Test
    public void shouldThrowExceptionWhenNoUsernameClaimFound() {
      // given
      final Map<String, Object> claims = Map.of("sub", "user@example.com");

      // when
      final var exception =
          assertThatExceptionOfType(OAuth2AuthenticationException.class)
              .isThrownBy(() -> converter.convert(claims))
              .actual();

      assertThat(exception.getMessage())
          .isEqualTo(
              "Neither username claim (%s) nor clientId claim (%s) could be found in the claims. Please check your OIDC configuration."
                  .formatted(USERNAME_CLAIM, APPLICATION_ID_CLAIM));
    }

    @Test
    public void shouldThrowExceptionWhenUsernameClaimIsNotAString() {
      // given
      final Map<String, Object> claims = Map.of(USERNAME_CLAIM, List.of("app-1", "app-2"));

      // when
      final var exception =
          assertThatExceptionOfType(IllegalArgumentException.class)
              .isThrownBy(() -> converter.convert(claims))
              .actual();

      assertThat(exception.getMessage())
          .isEqualTo("Value for $['email'] is not a string. Please check your OIDC configuration.");
    }

    @Test
    public void shouldExtractUsernameWhenPreferUsernameClaimIsTrueAndClientIdIsPresent() {
      when(oidcAuthenticationConfiguration.isPreferUsernameClaim()).thenReturn(true);
      final var tokenConverter = new TokenClaimsConverter(securityConfiguration, membershipService);

      final Map<String, Object> claims =
          Map.of(USERNAME_CLAIM, "my-user", APPLICATION_ID_CLAIM, "preferred");

      final var camundaAuthentication = tokenConverter.convert(claims);

      assertThat(camundaAuthentication).isNotNull();
      assertThat(camundaAuthentication.authenticatedClientId()).isNull();
      assertThat(camundaAuthentication.authenticatedUsername()).isEqualTo("my-user");
    }

    @Test
    public void shouldExtractClientIdWhenPreferUsernameClaimIsTrueAndUsernameIsNotPresent() {
      when(oidcAuthenticationConfiguration.isPreferUsernameClaim()).thenReturn(true);
      final var tokenConverter = new TokenClaimsConverter(securityConfiguration, membershipService);
      final Map<String, Object> claims =
          Map.of("differentUsernameClaim", "my-user", APPLICATION_ID_CLAIM, "preferred");

      final var camundaAuthentication = tokenConverter.convert(claims);

      assertThat(camundaAuthentication).isNotNull();
      assertThat(camundaAuthentication.authenticatedClientId()).isEqualTo("preferred");
      assertThat(camundaAuthentication.authenticatedUsername()).isNull();
    }
  }

  @Nested
  class GroupsClaimConfiguration {

    private static final String GROUPS_CLAIM = "$.groups[*].['name']";

    @Mock private SecurityConfiguration securityConfiguration;
    @Mock private AuthenticationConfiguration authenticationConfiguration;
    @Mock private OidcAuthenticationConfiguration oidcAuthenticationConfiguration;

    @BeforeEach
    public void setUp() throws Exception {
      MockitoAnnotations.openMocks(this).close();

      when(securityConfiguration.getAuthentication()).thenReturn(authenticationConfiguration);
      when(authenticationConfiguration.getOidc()).thenReturn(oidcAuthenticationConfiguration);
      when(oidcAuthenticationConfiguration.getUsernameClaim()).thenReturn("sub");
      when(oidcAuthenticationConfiguration.getClientIdClaim()).thenReturn("not-tested");
      when(oidcAuthenticationConfiguration.isGroupsClaimConfigured()).thenReturn(true);
      when(oidcAuthenticationConfiguration.getGroupsClaim()).thenReturn(GROUPS_CLAIM);

      membershipService = new NoDBMembershipService(securityConfiguration);

      converter = new TokenClaimsConverter(securityConfiguration, membershipService);
    }
  }
}
