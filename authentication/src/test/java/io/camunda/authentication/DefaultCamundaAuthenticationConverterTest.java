/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication;

import static io.camunda.zeebe.auth.Authorization.USER_TOKEN_CLAIMS;
import static org.assertj.core.api.Assertions.assertThat;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import io.camunda.authentication.entity.AuthenticationContext.AuthenticationContextBuilder;
import io.camunda.authentication.entity.CamundaJwtUser;
import io.camunda.authentication.entity.CamundaOidcUser;
import io.camunda.authentication.entity.CamundaUser.CamundaUserBuilder;
import io.camunda.authentication.entity.OAuthContext;
import io.camunda.security.auth.CamundaAuthenticationConverter;
import io.camunda.service.TenantServices.TenantDTO;
import io.camunda.zeebe.auth.Authorization;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.jwt.Jwt;

public class DefaultCamundaAuthenticationConverterTest {

  private CamundaAuthenticationConverter<Authentication> authenticationConverter;

  @BeforeEach
  void setup() throws Exception {
    authenticationConverter = new DefaultCamundaAuthenticationConverter();
  }

  @Test
  void shouldSupportCamundaPrincipal() {
    // given
    final var username = "username";
    final var authentication = getUsernamePasswordAuthenticationInContext(username);

    // when
    final var supports = authenticationConverter.supports(authentication);

    // then
    assertThat(supports).isTrue();
  }

  @Test
  void shouldNotSupportNoneCamundaPrincipal() {
    // given
    final var username = "username";
    final var authentication = new UsernamePasswordAuthenticationToken(username, null);

    // when
    final var supports = authenticationConverter.supports(authentication);

    // then
    assertThat(supports).isFalse();
  }

  @Test
  void authenticationContainsUsername() {
    // given
    final var username = "username";
    final var authentication = getUsernamePasswordAuthenticationInContext(username);

    // when
    final var camundaAuthentication = authenticationConverter.convert(authentication);

    // then
    assertThat(camundaAuthentication).isNotNull();
    assertThat(camundaAuthentication.authenticatedUsername()).isEqualTo(username);
    assertThat(camundaAuthentication.claims().get(Authorization.AUTHORIZED_USERNAME))
        .isEqualTo(username);
  }

  @Test
  void authenticationContainsExtraClaimsWithOidcAuth() {
    // given
    final String usernameClaim = "sub";
    final String usernameValue = "test-user";
    final var authentication = getOidcAuthenticationInContext(usernameClaim, usernameValue, "aud1");

    // when
    final var camundaAuthentication = authenticationConverter.convert(authentication);

    // then
    assertThat(camundaAuthentication.authenticatedUsername()).isEqualTo(usernameValue);
  }

  @Test
  void authenticationContainsExtraClaimsWithJwtAuth() {
    // given
    final String sub1 = "sub1";
    final String aud1 = "aud1";
    final var authentication = getJwtAuthenticationInContext(sub1, aud1);

    // when
    final var camundaAuthentication = authenticationConverter.convert(authentication);

    // then
    final var claims = (Map<String, Object>) camundaAuthentication.claims().get(USER_TOKEN_CLAIMS);
    assertThat(claims).isNotNull();
    assertThat(claims).containsKey("sub");
    assertThat(claims).containsKey("aud");
    assertThat(claims).containsKey("groups");
    assertThat(claims.get("sub")).isEqualTo(sub1);
    assertThat(claims.get("aud")).isEqualTo(aud1);
    assertThat(claims.get("groups")).isEqualTo(List.of("g1", "g2"));
  }

  @Test
  void authenticationContainsTenantIdsInAuthenticationContext() {
    // given
    final var username = "test-user";
    final var tenants =
        List.of(
            new TenantDTO(1L, "tenant-1", "Tenant One", "First"),
            new TenantDTO(2L, "tenant-2", "Tenant Two", "Second"));
    final var authenticationContext =
        new AuthenticationContextBuilder().withUsername(username).withTenants(tenants).build();

    final var principal =
        new CamundaOidcUser(
            new DefaultOidcUser(
                Collections.emptyList(),
                new OidcIdToken(
                    "tokenValue",
                    Instant.now(),
                    Instant.now().plusSeconds(3600),
                    Map.of("sub", username))),
            null,
            Collections.emptySet(),
            authenticationContext);

    final var auth = new OAuth2AuthenticationToken(principal, List.of(), "oidc");

    // when
    final var camundaAuthentication = authenticationConverter.convert(auth);

    // then
    assertThat(camundaAuthentication.authenticatedTenantIds())
        .containsExactlyInAnyOrder("tenant-1", "tenant-2");
    assertThat(camundaAuthentication.authenticatedUsername()).isEqualTo(username);
  }

  @Test
  void usernameIsSetInAuthenticationWhenOnAuthenticationContext() {
    // given
    final var username = "test-user";
    final var authenticationContext =
        new AuthenticationContextBuilder().withUsername(username).build();

    final var principal =
        new CamundaOidcUser(
            new DefaultOidcUser(
                Collections.emptyList(),
                new OidcIdToken(
                    "tokenValue",
                    Instant.now(),
                    Instant.now().plusSeconds(3600),
                    Map.of("sub", username))),
            null,
            Collections.emptySet(),
            authenticationContext);

    final var auth = new OAuth2AuthenticationToken(principal, List.of(), "oidc");

    // when
    final var camundaAuthentication = authenticationConverter.convert(auth);

    // then
    assertThat(camundaAuthentication.authenticatedUsername()).isEqualTo(username);
    assertThat(camundaAuthentication.authenticatedClientId()).isNull();
  }

  @Test
  void clientIdIsSetInAuthenticationWhenOnAuthenticationContext() {
    // given
    final var clientId = "my-application";
    final var authenticationContext =
        new AuthenticationContextBuilder().withClientId(clientId).build();

    final var principal =
        new CamundaOidcUser(
            new DefaultOidcUser(
                Collections.emptyList(),
                new OidcIdToken(
                    "tokenValue",
                    Instant.now(),
                    Instant.now().plusSeconds(3600),
                    Map.of("sub", clientId))),
            null,
            Collections.emptySet(),
            authenticationContext);

    final var auth = new OAuth2AuthenticationToken(principal, List.of(), "oidc");

    // when
    final var camundaAuthentication = authenticationConverter.convert(auth);

    // then
    assertThat(camundaAuthentication.authenticatedUsername()).isNull();
    assertThat(camundaAuthentication.authenticatedClientId()).isEqualTo(clientId);
  }

  private Authentication getJwtAuthenticationInContext(final String sub, final String aud) {
    final Jwt jwt =
        new Jwt(
            JWT.create()
                .withIssuer("issuer1")
                .withAudience(aud)
                .withSubject(sub)
                .sign(Algorithm.none()),
            Instant.ofEpochSecond(10),
            Instant.ofEpochSecond(100),
            Map.of("alg", "RSA256"),
            Map.of("sub", sub, "aud", aud, "groups", List.of("g1", "g2")));

    return new CamundaJwtAuthenticationToken(
        jwt,
        new CamundaJwtUser(
            jwt,
            new OAuthContext(
                new HashSet<>(),
                new AuthenticationContextBuilder()
                    .withUsername(sub)
                    .withGroups(List.of("g1", "g2"))
                    .build())),
        null,
        null);
  }

  private Authentication getOidcAuthenticationInContext(
      final String usernameClaim, final String usernameValue, final String aud) {
    final String tokenValue = "{}";
    final Instant tokenIssuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    final Instant tokenExpiresAt = tokenIssuedAt.plus(1, ChronoUnit.DAYS);

    return new OAuth2AuthenticationToken(
        new CamundaOidcUser(
            new DefaultOidcUser(
                Collections.emptyList(),
                new OidcIdToken(
                    tokenValue,
                    tokenIssuedAt,
                    tokenExpiresAt,
                    Map.of("aud", aud, usernameClaim, usernameValue))),
            null,
            Collections.emptySet(),
            new AuthenticationContextBuilder().withUsername(usernameValue).build()),
        List.of(),
        "oidc");
  }

  private Authentication getUsernamePasswordAuthenticationInContext(final String username) {
    return new UsernamePasswordAuthenticationToken(
        CamundaUserBuilder.aCamundaUser()
            .withUsername(username)
            .withPassword("admin")
            .withUserKey(1L)
            .build(),
        null);
  }
}
