/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Tests for {@link SpringAuthenticationAdapter}. Validates JWT claim extraction, OidcUser
 * conversion, and SecurityContext integration.
 */
class SpringAuthenticationAdapterTest {

  private final SpringAuthenticationAdapter adapter = new SpringAuthenticationAdapter();

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  // -- getCurrentUsername() --

  @Test
  void shouldReturnPreferredUsernameFromJwt() {
    // given
    final Jwt jwt = createJwt(Map.of("preferred_username", "jane.doe", "sub", "sub-123"));
    setJwtAuthentication(jwt);

    // when
    final var username = adapter.getCurrentUsername();

    // then
    assertThat(username).isPresent().hasValue("jane.doe");
  }

  @Test
  void shouldFallBackToSubWhenPreferredUsernameIsNull() {
    // given
    final Jwt jwt = createJwt(Map.of("sub", "sub-456"));
    setJwtAuthentication(jwt);

    // when
    final var username = adapter.getCurrentUsername();

    // then
    assertThat(username).isPresent().hasValue("sub-456");
  }

  @Test
  void shouldReturnEmptyUsernameWhenNotAuthenticated() {
    // given — no authentication set

    // when
    final var username = adapter.getCurrentUsername();

    // then
    assertThat(username).isEmpty();
  }

  // -- getCurrentClientId() --

  @Test
  void shouldReturnAzpFromJwt() {
    // given
    final Jwt jwt = createJwt(Map.of("sub", "user", "azp", "my-client-app"));
    setJwtAuthentication(jwt);

    // when
    final var clientId = adapter.getCurrentClientId();

    // then
    assertThat(clientId).isPresent().hasValue("my-client-app");
  }

  @Test
  void shouldFallBackToClientIdClaimWhenAzpIsNull() {
    // given
    final Jwt jwt = createJwt(Map.of("sub", "user", "client_id", "service-account"));
    setJwtAuthentication(jwt);

    // when
    final var clientId = adapter.getCurrentClientId();

    // then
    assertThat(clientId).isPresent().hasValue("service-account");
  }

  @Test
  void shouldReturnNullClientIdInsideOptionalWhenNeitherAzpNorClientIdPresent() {
    // given
    final Jwt jwt = createJwt(Map.of("sub", "user"));
    setJwtAuthentication(jwt);

    // when
    final var clientId = adapter.getCurrentClientId();

    // then
    // getJwt() returns Optional.of(jwt), then map extracts null for azp/client_id.
    // Optional.map(f) where f returns null yields Optional.empty().
    assertThat(clientId).isEmpty();
  }

  // -- getCurrentToken() --

  @Test
  void shouldReturnTokenValue() {
    // given
    final Jwt jwt = createJwt(Map.of("sub", "user"));
    setJwtAuthentication(jwt);

    // when
    final var token = adapter.getCurrentToken();

    // then
    assertThat(token).isPresent().hasValue("test-token-value");
  }

  @Test
  void shouldReturnEmptyTokenWhenNotAuthenticated() {
    // when
    final var token = adapter.getCurrentToken();

    // then
    assertThat(token).isEmpty();
  }

  // -- getCurrentGroupIds/RoleIds/TenantIds --

  @Test
  void shouldReturnGroupIdsFromJwt() {
    // given
    final Jwt jwt =
        createJwt(
            Map.of("sub", "user", "groups", List.of("admin", "developers")));
    setJwtAuthentication(jwt);

    // when
    final var groups = adapter.getCurrentGroupIds();

    // then
    assertThat(groups).containsExactly("admin", "developers");
  }

  @Test
  void shouldReturnEmptyGroupsWhenClaimMissing() {
    // given
    final Jwt jwt = createJwt(Map.of("sub", "user"));
    setJwtAuthentication(jwt);

    // when
    final var groups = adapter.getCurrentGroupIds();

    // then
    assertThat(groups).isEmpty();
  }

  @Test
  void shouldReturnRoleIdsFromJwt() {
    // given
    final Jwt jwt =
        createJwt(Map.of("sub", "user", "roles", List.of("viewer", "editor")));
    setJwtAuthentication(jwt);

    // when
    final var roles = adapter.getCurrentRoleIds();

    // then
    assertThat(roles).containsExactly("viewer", "editor");
  }

  @Test
  void shouldReturnTenantIdsFromJwt() {
    // given
    final Jwt jwt =
        createJwt(Map.of("sub", "user", "tenants", List.of("tenant-a", "tenant-b")));
    setJwtAuthentication(jwt);

    // when
    final var tenants = adapter.getCurrentTenantIds();

    // then
    assertThat(tenants).containsExactly("tenant-a", "tenant-b");
  }

  @Test
  void shouldReturnEmptyListsWhenNotAuthenticated() {
    // when
    final var groups = adapter.getCurrentGroupIds();
    final var roles = adapter.getCurrentRoleIds();
    final var tenants = adapter.getCurrentTenantIds();

    // then
    assertThat(groups).isEmpty();
    assertThat(roles).isEmpty();
    assertThat(tenants).isEmpty();
  }

  // -- getCurrentClaims() --

  @Test
  void shouldReturnAllClaimsFromJwt() {
    // given
    final Jwt jwt =
        createJwt(
            Map.of("sub", "user", "preferred_username", "jane", "custom_claim", "custom_value"));
    setJwtAuthentication(jwt);

    // when
    final var claims = adapter.getCurrentClaims();

    // then
    assertThat(claims).containsEntry("sub", "user");
    assertThat(claims).containsEntry("preferred_username", "jane");
    assertThat(claims).containsEntry("custom_claim", "custom_value");
  }

  @Test
  void shouldReturnEmptyClaimsWhenNotAuthenticated() {
    // when
    final var claims = adapter.getCurrentClaims();

    // then
    assertThat(claims).isEmpty();
  }

  // -- isAuthenticated() --

  @Test
  void shouldReturnTrueWhenAuthenticated() {
    // given — JwtAuthenticationToken with authorities sets authenticated=true
    final Jwt jwt = createJwt(Map.of("sub", "user"));
    final var auth =
        new JwtAuthenticationToken(
            jwt, List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")));
    SecurityContextHolder.getContext().setAuthentication(auth);

    // when
    final var result = adapter.isAuthenticated();

    // then
    assertThat(result).isTrue();
  }

  @Test
  void shouldReturnFalseWhenNotAuthenticated() {
    // when
    final var result = adapter.isAuthenticated();

    // then
    assertThat(result).isFalse();
  }

  @Test
  void shouldReturnFalseForUnauthenticatedToken() {
    // given
    final var auth = new TestingAuthenticationToken("user", "pass");
    auth.setAuthenticated(false);
    SecurityContextHolder.getContext().setAuthentication(auth);

    // when
    final var result = adapter.isAuthenticated();

    // then
    assertThat(result).isFalse();
  }

  // -- OidcUser support --

  @Test
  void shouldCreateSyntheticJwtFromOidcUser() {
    // given
    final var idToken =
        new OidcIdToken(
            "oidc-id-token-value",
            Instant.now(),
            Instant.now().plusSeconds(300),
            Map.of(
                "sub", "oidc-user-123",
                "preferred_username", "oidc.jane",
                "iss", "https://issuer.example.com",
                "azp", "oidc-client"));
    final OidcUser oidcUser = new DefaultOidcUser(List.of(), idToken);
    final var auth = new TestingAuthenticationToken(oidcUser, null);
    auth.setAuthenticated(true);
    SecurityContextHolder.getContext().setAuthentication(auth);

    // when
    final var username = adapter.getCurrentUsername();
    final var clientId = adapter.getCurrentClientId();
    final var token = adapter.getCurrentToken();
    final var claims = adapter.getCurrentClaims();

    // then
    assertThat(username).isPresent().hasValue("oidc.jane");
    assertThat(clientId).isPresent().hasValue("oidc-client");
    assertThat(token).isPresent().hasValue("oidc-id-token-value");
    assertThat(claims).containsEntry("sub", "oidc-user-123");
    assertThat(claims).containsEntry("preferred_username", "oidc.jane");
  }

  @Test
  void shouldFallBackToSubForOidcUserWithoutPreferredUsername() {
    // given
    final var idToken =
        new OidcIdToken(
            "oidc-token",
            Instant.now(),
            Instant.now().plusSeconds(300),
            Map.of("sub", "oidc-sub-only", "iss", "https://issuer.example.com"));
    final OidcUser oidcUser = new DefaultOidcUser(List.of(), idToken);
    final var auth = new TestingAuthenticationToken(oidcUser, null);
    auth.setAuthenticated(true);
    SecurityContextHolder.getContext().setAuthentication(auth);

    // when
    final var username = adapter.getCurrentUsername();

    // then
    assertThat(username).isPresent().hasValue("oidc-sub-only");
  }

  @Test
  void shouldReturnEmptyForNonJwtNonOidcAuthentication() {
    // given — plain TestingAuthenticationToken with string principal
    final var auth = new TestingAuthenticationToken("simple-user", "pass");
    auth.setAuthenticated(true);
    SecurityContextHolder.getContext().setAuthentication(auth);

    // when
    final var username = adapter.getCurrentUsername();
    final var token = adapter.getCurrentToken();
    final var claims = adapter.getCurrentClaims();

    // then
    assertThat(username).isEmpty();
    assertThat(token).isEmpty();
    assertThat(claims).isEmpty();
  }

  // -- Helper methods --

  private Jwt createJwt(final Map<String, Object> claims) {
    final var builder =
        Jwt.withTokenValue("test-token-value")
            .header("alg", "RS256")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(300));
    claims.forEach(builder::claim);
    return builder.build();
  }

  private void setJwtAuthentication(final Jwt jwt) {
    final var auth = new JwtAuthenticationToken(jwt);
    SecurityContextHolder.getContext().setAuthentication(auth);
  }
}
