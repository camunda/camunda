/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.configuration.PhysicalTenantIdpRegistry;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

public class TenantLoginControllerTest {

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  public void shouldReturn404ForUnknownTenant() {
    final var controller =
        new TenantLoginController(
            new PhysicalTenantIdpRegistry(Map.of("default-engine", List.of("default"))));

    final var response = controller.picker("unknown-tenant");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).isNull();
  }

  @Test
  public void shouldReturnAllowedIdpsForKnownTenantWithTenantQueryParam() {
    final var controller =
        new TenantLoginController(
            new PhysicalTenantIdpRegistry(
                Map.of("risk-production", List.of("default", "provider-a"))));

    final var response = controller.picker("risk-production");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    final var body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.tenantId()).isEqualTo("risk-production");
    assertThat(body.idps())
        .extracting(TenantLoginController.IdpOption::id)
        .containsExactly("default", "provider-a");
    assertThat(body.idps())
        .extracting(TenantLoginController.IdpOption::loginUrl)
        .containsExactly(
            "/oauth2/authorization/default?tenant=risk-production",
            "/oauth2/authorization/provider-a?tenant=risk-production");
  }

  @Test
  public void shouldReturnEmptyIdpListWhenTenantHasNoIdps() {
    final var controller =
        new TenantLoginController(
            new PhysicalTenantIdpRegistry(Map.of("default-engine", List.of())));

    final var response = controller.picker("default-engine");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().idps()).isEmpty();
  }

  @Test
  public void shouldReturn409WhenAlreadyAuthenticatedViaOAuth2() {
    SecurityContextHolder.getContext().setAuthentication(oauthToken("default"));
    final var controller =
        new TenantLoginController(
            new PhysicalTenantIdpRegistry(Map.of("default-engine", List.of("default"))));

    final var response = controller.picker("default-engine");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody()).isNull();
  }

  @Test
  public void shouldReturn409WhenAlreadyAuthenticatedViaOtherMechanism() {
    SecurityContextHolder.getContext()
        .setAuthentication(new TestingAuthenticationToken("user", "pw", "ROLE_USER"));
    final var controller =
        new TenantLoginController(
            new PhysicalTenantIdpRegistry(Map.of("default-engine", List.of("default"))));

    final var response = controller.picker("default-engine");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  public void shouldEncodeTenantIdsWithSpecialChars() {
    final var controller =
        new TenantLoginController(
            new PhysicalTenantIdpRegistry(Map.of("tenant with-dash", List.of("default"))));

    final var response = controller.picker("tenant with-dash");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().idps())
        .extracting(TenantLoginController.IdpOption::loginUrl)
        .containsExactly("/oauth2/authorization/default?tenant=tenant+with-dash");
  }

  private static OAuth2AuthenticationToken oauthToken(final String registrationId) {
    final var user =
        new DefaultOAuth2User(
            Set.of(new SimpleGrantedAuthority("ROLE_USER")), Map.of("sub", "user1"), "sub");
    return new OAuth2AuthenticationToken(user, user.getAuthorities(), registrationId);
  }
}
