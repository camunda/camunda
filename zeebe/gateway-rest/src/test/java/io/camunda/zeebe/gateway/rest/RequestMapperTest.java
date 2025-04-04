/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest;

import static io.camunda.zeebe.auth.Authorization.USER_TOKEN_CLAIM_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import io.camunda.authentication.entity.AuthenticationContext;
import io.camunda.authentication.entity.CamundaOidcUser;
import io.camunda.authentication.entity.CamundaUser.CamundaUserBuilder;
import io.camunda.zeebe.auth.Authorization;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

class RequestMapperTest {

  @Mock private RequestAttributes requestAttributes;

  @BeforeEach
  void setup() throws Exception {
    MockitoAnnotations.openMocks(this).close();
    RequestContextHolder.setRequestAttributes(requestAttributes);
  }

  @Test
  void tokenContainsUsernameWithBasicAuth() {

    // given
    final var username = "username";
    setUsernamePasswordAuthenticationInContext(username);

    // when
    final var claims = RequestMapper.getAuthentication().claims();

    // then
    assertNotNull(claims);
    assertThat(claims).containsKey(Authorization.AUTHORIZED_USERNAME);
    assertThat(claims.get(Authorization.AUTHORIZED_USERNAME)).isEqualTo(username);
  }

  @Test
  void tokenContainsExtraClaimsWithOidcAuth() {
    // given
    final String usernameClaim = "sub";
    final String usernameValue = "test-user";
    setOidcAuthenticationInContext(usernameClaim, usernameValue, "aud1");

    // when
    final var authenticatedUsername = RequestMapper.getAuthentication().authenticatedUsername();

    // then
    assertThat(authenticatedUsername).isEqualTo(usernameValue);
  }

  @Test
  void tokenContainsExtraClaimsWithJwtAuth() {

    // given
    final String sub1 = "sub1";
    final String aud1 = "aud1";
    setJwtAuthenticationInContext(sub1, aud1);

    // when
    final var claims = RequestMapper.getAuthentication().claims();

    // then
    assertNotNull(claims);
    assertThat(claims).containsKey(USER_TOKEN_CLAIM_PREFIX + "sub");
    assertThat(claims).containsKey(USER_TOKEN_CLAIM_PREFIX + "aud");
    assertThat(claims).containsKey(USER_TOKEN_CLAIM_PREFIX + "groups");
    assertThat(claims.get(USER_TOKEN_CLAIM_PREFIX + "sub")).isEqualTo(sub1);
    assertThat(claims.get(USER_TOKEN_CLAIM_PREFIX + "aud")).isEqualTo(aud1);
    assertThat(claims.get(USER_TOKEN_CLAIM_PREFIX + "groups")).isEqualTo(List.of("g1", "g2"));
  }

  private void setJwtAuthenticationInContext(final String sub, final String aud) {
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
    final JwtAuthenticationToken jwtAuthenticationToken = new JwtAuthenticationToken(jwt);
    SecurityContextHolder.getContext().setAuthentication(jwtAuthenticationToken);
  }

  private void setOidcAuthenticationInContext(
      final String usernameClaim, final String usernameValue, final String aud) {
    final String tokenValue = "{}";
    final Instant tokenIssuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    final Instant tokenExpiresAt = tokenIssuedAt.plus(1, ChronoUnit.DAYS);

    final var oauth2Authentication =
        new OAuth2AuthenticationToken(
            new CamundaOidcUser(
                new DefaultOidcUser(
                    Collections.emptyList(),
                    new OidcIdToken(
                        tokenValue,
                        tokenIssuedAt,
                        tokenExpiresAt,
                        Map.of("aud", aud, usernameClaim, usernameValue))),
                Collections.emptySet(),
                Collections.emptySet(),
                new AuthenticationContext(
                    usernameValue, List.of(), List.of(), List.of(), List.of())),
            List.of(),
            "oidc");

    SecurityContextHolder.getContext().setAuthentication(oauth2Authentication);
  }

  private void setUsernamePasswordAuthenticationInContext(final String username) {
    final UsernamePasswordAuthenticationToken authenticationToken =
        new UsernamePasswordAuthenticationToken(
            CamundaUserBuilder.aCamundaUser()
                .withUsername(username)
                .withPassword("admin")
                .withUserKey(1L)
                .build(),
            null);
    SecurityContextHolder.getContext().setAuthentication(authenticationToken);
  }
}
