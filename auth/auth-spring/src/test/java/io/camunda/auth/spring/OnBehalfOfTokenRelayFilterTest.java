/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class OnBehalfOfTokenRelayFilterTest {

  @Mock private OAuth2AuthorizedClientManager authorizedClientManager;
  @Mock private OAuth2AuthorizedClient authorizedClient;
  @Mock private OAuth2AccessToken accessToken;
  @Mock private FilterChain filterChain;

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void shouldExchangeTokenForJwtAuthentication() throws Exception {
    // given
    setJwtAuthentication();
    when(accessToken.getTokenValue()).thenReturn("exchanged-token");
    when(authorizedClient.getAccessToken()).thenReturn(accessToken);
    when(authorizedClientManager.authorize(any())).thenReturn(authorizedClient);
    var filter = new OnBehalfOfTokenRelayFilter(authorizedClientManager, "my-registration");
    var request = new MockHttpServletRequest();
    var response = new MockHttpServletResponse();

    // when
    filter.doFilterInternal(request, response, filterChain);

    // then
    verify(authorizedClientManager).authorize(any());
    verify(filterChain).doFilter(request, response);
    assertThat(request.getAttribute(OnBehalfOfTokenRelayFilter.OBO_TOKEN_ATTRIBUTE))
        .isEqualTo("exchanged-token");
  }

  @Test
  void shouldSkipNonJwtAuthentication() throws Exception {
    // given
    SecurityContextHolder.getContext()
        .setAuthentication(new TestingAuthenticationToken("user", "pass", "ROLE_USER"));
    var filter = new OnBehalfOfTokenRelayFilter(authorizedClientManager, "my-registration");
    var request = new MockHttpServletRequest();

    // when
    var result = filter.shouldNotFilter(request);

    // then
    assertThat(result).isTrue();
    verifyNoInteractions(authorizedClientManager);
  }

  @Test
  void shouldContinueOnExchangeFailure() throws Exception {
    // given
    setJwtAuthentication();
    when(authorizedClientManager.authorize(any()))
        .thenThrow(new RuntimeException("exchange failed"));
    var filter = new OnBehalfOfTokenRelayFilter(authorizedClientManager, "my-registration");
    var request = new MockHttpServletRequest();
    var response = new MockHttpServletResponse();

    // when
    filter.doFilterInternal(request, response, filterChain);

    // then
    verify(filterChain).doFilter(request, response);
    assertThat(request.getAttribute(OnBehalfOfTokenRelayFilter.OBO_TOKEN_ATTRIBUTE)).isNull();
  }

  @Test
  void shouldStoreTokenAsRequestAttribute() throws Exception {
    // given
    setJwtAuthentication();
    when(accessToken.getTokenValue()).thenReturn("my-obo-token");
    when(authorizedClient.getAccessToken()).thenReturn(accessToken);
    when(authorizedClientManager.authorize(any())).thenReturn(authorizedClient);
    var filter = new OnBehalfOfTokenRelayFilter(authorizedClientManager, "my-registration");
    var request = new MockHttpServletRequest();
    var response = new MockHttpServletResponse();

    // when
    filter.doFilterInternal(request, response, filterChain);

    // then
    assertThat(request.getAttribute(OnBehalfOfTokenRelayFilter.OBO_TOKEN_ATTRIBUTE))
        .isEqualTo("my-obo-token");
  }

  private void setJwtAuthentication() {
    Jwt jwt =
        Jwt.withTokenValue("original-token")
            .header("alg", "RS256")
            .claim("sub", "user1")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(300))
            .build();
    JwtAuthenticationToken jwtAuth = new JwtAuthenticationToken(jwt);
    SecurityContextHolder.getContext().setAuthentication(jwtAuth);
  }
}
