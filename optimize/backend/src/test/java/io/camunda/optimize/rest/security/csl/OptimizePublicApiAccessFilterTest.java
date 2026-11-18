/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.api.model.CamundaAuthentication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
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

  private final CamundaAuthenticationProvider authenticationProvider =
      mock(CamundaAuthenticationProvider.class);

  private final OptimizePublicApiAccessFilter filter =
      OptimizePublicApiAccessFilter.forSelfManaged(authenticationProvider);

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/api/public/collection",
        "/api/public",
        "/api/ingestion/variable",
        "/api/p%75blic/collection",
        "/api/ingestion/vari%61ble"
      })
  void shouldAcceptApiClientToken(final String path) throws Exception {
    // given
    authenticate(bearer(null));
    classifyAsClient();

    // when
    final var result = filter(filter, path);

    // then
    assertThat(result.response().getStatus()).isEqualTo(200);
    assertThat(result.chain().getRequest()).isNotNull();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/api/public/collection",
        "/api/ingestion/variable",
        "/api/p%75blic/collection",
        "/api/ingestion/vari%61ble"
      })
  void shouldRejectTheSession(final String path) throws Exception {
    // given a webapp session, which is not a bearer token
    authenticate(new TestingAuthenticationToken("alice", null, "ROLE_USER"));

    // when
    final var result = filter(filter, path);

    // then
    assertThat(result.response().getStatus()).isEqualTo(401);
    assertThat(result.chain().getRequest()).isNull();
  }

  @Test
  void shouldRejectUserToken() throws Exception {
    // given a bearer token that CSL classifies as a user's
    authenticate(bearer(null));
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(CamundaAuthentication.of(b -> b.user("alice")));

    // when
    final var result = filter(filter, "/api/public/collection");

    // then
    assertThat(result.response().getStatus()).isEqualTo(401);
    assertThat(result.chain().getRequest()).isNull();
  }

  @Test
  void shouldRejectTokenThatCannotBeClassified() throws Exception {
    // given a bearer token with neither the username nor the client id claim
    authenticate(bearer(null));
    when(authenticationProvider.getCamundaAuthentication())
        .thenThrow(new IllegalArgumentException("Neither username claim nor clientId claim"));

    // when
    final var result = filter(filter, "/api/public/collection");

    // then
    assertThat(result.response().getStatus()).isEqualTo(401);
    assertThat(result.chain().getRequest()).isNull();
  }

  @Test
  void shouldRejectTokenWithoutCamundaAuthentication() throws Exception {
    // given
    authenticate(bearer(null));
    when(authenticationProvider.getCamundaAuthentication()).thenReturn(null);

    // when
    final var result = filter(filter, "/api/public/collection");

    // then
    assertThat(result.response().getStatus()).isEqualTo(401);
    assertThat(result.chain().getRequest()).isNull();
  }

  @Test
  void shouldAcceptSaasApiClientTokenWithThisClusterId() throws Exception {
    // given
    final var saas = OptimizePublicApiAccessFilter.forSaas(authenticationProvider, "cluster-1");
    authenticate(bearer("cluster-1"));
    classifyAsClient();

    // when
    final var result = filter(saas, "/api/public/collection");

    // then
    assertThat(result.response().getStatus()).isEqualTo(200);
    assertThat(result.chain().getRequest()).isNotNull();
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = "cluster-2")
  void shouldRejectSaasApiClientTokenWithoutThisClusterId(final String clusterId) throws Exception {
    // given an API client's token without the cluster id claim, or with another cluster's id
    final var saas = OptimizePublicApiAccessFilter.forSaas(authenticationProvider, "cluster-1");
    authenticate(bearer(clusterId));
    classifyAsClient();

    // when
    final var result = filter(saas, "/api/public/collection");

    // then
    assertThat(result.response().getStatus()).isEqualTo(401);
    assertThat(result.chain().getRequest()).isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"/api/report/some-id", "/api/publicity", "/api/ingestion/other"})
  void shouldLeaveOtherPathsAlone(final String path) throws Exception {
    // given a session, which the rest of the API accepts
    authenticate(new TestingAuthenticationToken("alice", null, "ROLE_USER"));

    // when
    final var result = filter(filter, path);

    // then
    assertThat(result.response().getStatus()).isEqualTo(200);
    assertThat(result.chain().getRequest()).isNotNull();
  }

  private void classifyAsClient() {
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(CamundaAuthentication.of(b -> b.clientId("client-1")));
  }

  private static Result filter(final OptimizePublicApiAccessFilter filter, final String path)
      throws Exception {
    final var request = new MockHttpServletRequest("GET", path);
    final var response = new MockHttpServletResponse();
    final var chain = new MockFilterChain();
    filter.doFilter(request, response, chain);
    return new Result(response, chain);
  }

  private static void authenticate(final Authentication authentication) {
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  /** A bearer token with the SaaS cluster id claim, left out when it is null. */
  private static JwtAuthenticationToken bearer(final String clusterId) {
    final var token = Jwt.withTokenValue("token").header("alg", "RS256").subject("subject");
    if (clusterId != null) {
      token.claim(OptimizeCloudClusterValidator.CLUSTER_CLAIM, clusterId);
    }
    return new JwtAuthenticationToken(token.build());
  }

  private record Result(MockHttpServletResponse response, MockFilterChain chain) {}
}
