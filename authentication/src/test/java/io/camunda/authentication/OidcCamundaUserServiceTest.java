/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.authentication.entity.AuthenticationContext.AuthenticationContextBuilder;
import io.camunda.authentication.entity.CamundaJwtUser;
import io.camunda.authentication.entity.CamundaOidcUser;
import io.camunda.authentication.entity.OAuthContext;
import io.camunda.authentication.service.OidcCamundaUserService;
import jakarta.json.Json;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.jwt.Jwt;

public class OidcCamundaUserServiceTest {
  private static final String TOKEN_VALUE =
      "{\"access_token\":\"test-access-token\",\"token_type\":\"Bearer\",\"expires_in\":3600}";

  private OidcCamundaUserService oidcCamundaUserService;

  @BeforeEach
  public void setUp() throws Exception {
    oidcCamundaUserService = new OidcCamundaUserService(null, null);
  }

  @Test
  public void givenCamundaOidcUserWithAccessTokenWhenGetUserTokenThenAccessTokenIsReturned() {
    final var principal =
        new CamundaOidcUser(
            new DefaultOidcUser(
                Collections.emptyList(),
                new OidcIdToken(
                    TOKEN_VALUE,
                    Instant.now(),
                    Instant.now().plusSeconds(3600),
                    Map.of("sub", "not-tested"))),
            "test-access-token",
            Collections.emptySet(),
            null);

    final var auth = new OAuth2AuthenticationToken(principal, List.of(), "oidc");
    SecurityContextHolder.getContext().setAuthentication(auth);

    final var expectedToken = Json.createValue("test-access-token").toString();
    assertThat(oidcCamundaUserService.getUserToken()).isEqualTo(expectedToken);
  }

  @Test
  public void givenCamundaOidcUserWithIdTokenWhenGetUserTokenThenIdTokenIsReturned() {
    final var principal =
        new CamundaOidcUser(
            new DefaultOidcUser(
                Collections.emptyList(),
                new OidcIdToken(
                    TOKEN_VALUE,
                    Instant.now(),
                    Instant.now().plusSeconds(3600),
                    Map.of("sub", "not-tested"))),
            null,
            Collections.emptySet(),
            null);

    final var auth = new OAuth2AuthenticationToken(principal, List.of(), "oidc");
    SecurityContextHolder.getContext().setAuthentication(auth);

    final var expectedToken = Json.createValue(TOKEN_VALUE).toString();
    assertThat(oidcCamundaUserService.getUserToken()).isEqualTo(expectedToken);
  }

  @Test
  public void givenCamundaJwtUserWhenGetUserTokenThenNullIsReturned() {
    final var jwt =
        new Jwt(
            TOKEN_VALUE,
            Instant.now(),
            Instant.now().plusSeconds(3600),
            Map.of("sub", "not-tested"),
            Map.of("access_token", TOKEN_VALUE));

    final var camundaJwtAuthenticationToken =
        new CamundaJwtAuthenticationToken(
            jwt,
            new CamundaJwtUser(
                jwt,
                new OAuthContext(
                    new HashSet<>(),
                    new AuthenticationContextBuilder()
                        .withUsername("test-user")
                        .withGroups(List.of("g1", "g2"))
                        .build())),
            null,
            null);
    SecurityContextHolder.getContext().setAuthentication(camundaJwtAuthenticationToken);

    Assertions.assertThatThrownBy(() -> oidcCamundaUserService.getUserToken())
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining(
            "Not supported for token class: io.camunda.authentication.entity.CamundaJwtUser");
  }
}
