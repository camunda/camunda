/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class OptimizePublicApiAccessFilterTest {

  private static final String PUBLIC_API_AUDIENCE = "optimize-public-api";

  private final OptimizePublicApiAccessFilter filter =
      new OptimizePublicApiAccessFilter(PUBLIC_API_AUDIENCE);

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @ParameterizedTest
  @ValueSource(strings = {"/api/public/collection", "/api/public", "/api/ingestion/variable"})
  void shouldAcceptBearerTokenWithThePublicApiAudience(final String path) throws Exception {
    // given
    authenticate(bearer(PUBLIC_API_AUDIENCE));

    // when
    final var result = filter(path);

    // then
    assertThat(result.response().getStatus()).isEqualTo(200);
    assertThat(result.chain().getRequest()).isNotNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"/api/public/collection", "/api/ingestion/variable"})
  void shouldRejectTheSession(final String path) throws Exception {
    // given a webapp session, which is not a bearer token
    authenticate(new TestingAuthenticationToken("alice", null, "ROLE_USER"));

    // when
    final var result = filter(path);

    // then
    assertThat(result.response().getStatus()).isEqualTo(401);
    assertThat(result.chain().getRequest()).isNull();
  }

  @Test
  void shouldRejectBearerTokenWithoutThePublicApiAudience() throws Exception {
    // given a user's login token, which carries the webapp audience
    authenticate(bearer("optimize-webapp"));

    // when
    final var result = filter("/api/public/collection");

    // then
    assertThat(result.response().getStatus()).isEqualTo(401);
    assertThat(result.chain().getRequest()).isNull();
  }

  @Test
  void shouldRejectBearerTokenWithoutAudience() throws Exception {
    // given a token without an 'aud' claim
    authenticate(
        new JwtAuthenticationToken(
            Jwt.withTokenValue("token").header("alg", "RS256").subject("client").build()));

    // when
    final var result = filter("/api/public/collection");

    // then
    assertThat(result.response().getStatus()).isEqualTo(401);
    assertThat(result.chain().getRequest()).isNull();
  }

  @Test
  void shouldRejectEveryRequestWhenNoPublicApiAudienceIsConfigured() throws Exception {
    // given
    final var unconfigured = new OptimizePublicApiAccessFilter(null);
    authenticate(bearer(PUBLIC_API_AUDIENCE));
    final var request = new MockHttpServletRequest("GET", "/api/public/collection");
    final var response = new MockHttpServletResponse();
    final var chain = new MockFilterChain();

    // when
    unconfigured.doFilter(request, response, chain);

    // then
    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(chain.getRequest()).isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"/api/report/some-id", "/api/publicity", "/api/ingestion/other"})
  void shouldLeaveOtherPathsAlone(final String path) throws Exception {
    // given a session, which the rest of the API accepts
    authenticate(new TestingAuthenticationToken("alice", null, "ROLE_USER"));

    // when
    final var result = filter(path);

    // then
    assertThat(result.response().getStatus()).isEqualTo(200);
    assertThat(result.chain().getRequest()).isNotNull();
  }

  private Result filter(final String path) throws Exception {
    final var request = new MockHttpServletRequest("GET", path);
    final var response = new MockHttpServletResponse();
    final var chain = new MockFilterChain();
    filter.doFilter(request, response, chain);
    return new Result(response, chain);
  }

  private static void authenticate(final Authentication authentication) {
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  private static JwtAuthenticationToken bearer(final String audience) {
    return new JwtAuthenticationToken(
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .subject("client")
            .audience(List.of(audience))
            .build());
  }

  private record Result(MockHttpServletResponse response, MockFilterChain chain) {}
}
