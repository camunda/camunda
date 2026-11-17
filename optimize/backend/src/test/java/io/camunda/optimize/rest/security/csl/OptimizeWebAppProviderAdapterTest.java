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
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

class OptimizeWebAppProviderAdapterTest {

  private final OptimizeWebAppProviderAdapter adapter = new OptimizeWebAppProviderAdapter();

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void shouldClaimRequestsOfALoginSession() {
    // given
    SecurityContextHolder.getContext().setAuthentication(loginSession());

    // when
    final var webApp = adapter.webAppFor(new MockHttpServletRequest("GET", "/api/entities"));

    // then
    assertThat(webApp).contains("optimize");
  }

  @Test
  void shouldPassThroughRequestsThatAreNotALoginSession() {
    // A bearer request is authorized by the audience check of the API chain, not by the component
    // check.
    // given
    SecurityContextHolder.getContext()
        .setAuthentication(new TestingAuthenticationToken("client", "n/a"));

    // when
    final var webApp = adapter.webAppFor(new MockHttpServletRequest("GET", "/api/entities"));

    // then
    assertThat(webApp).isEmpty();
  }

  @Test
  void shouldPassThroughUnauthenticatedRequests() {
    // when
    final var webApp = adapter.webAppFor(new MockHttpServletRequest("GET", "/"));

    // then
    assertThat(webApp).isEmpty();
  }

  private static OAuth2AuthenticationToken loginSession() {
    final OidcIdToken idToken = new OidcIdToken("id-token", null, null, Map.of("sub", "kermit"));
    return new OAuth2AuthenticationToken(
        new DefaultOidcUser(AuthorityUtils.NO_AUTHORITIES, idToken, "sub"), List.of(), "oidc");
  }
}
