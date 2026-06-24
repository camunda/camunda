/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.optimize.rest.exceptions.NotAuthorizedException;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.OptimizeApiConfiguration;
import io.camunda.optimize.service.util.configuration.security.AuthConfiguration;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

  private static final String USER_SUBJECT = "user123";

  @Mock private TerminatedSessionService terminatedSessionService;
  @Mock private ConfigurationService configurationService;
  @Mock private AuthConfiguration authConfiguration;
  @Mock private OptimizeApiConfiguration apiConfiguration;

  private SessionService sessionService;

  @BeforeEach
  void setUp() {
    when(authConfiguration.getTokenSecret()).thenReturn(Optional.empty());
    when(configurationService.getAuthConfiguration()).thenReturn(authConfiguration);
    when(configurationService.getOptimizeApiConfiguration()).thenReturn(apiConfiguration);
    sessionService = new SessionService(terminatedSessionService, configurationService);
  }

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void shouldReturnSubjectFromBearerTokenWhenFlagEnabledAndJwtPresentInSecurityContext() {
    // given
    when(apiConfiguration.isJwtAuthForApiEnabled()).thenReturn(true);
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(buildJwt()));

    // when — bearer path is taken before the cookie path so no cookie stub is needed
    final String user =
        sessionService.getRequestUserOrFailNotAuthorized(mock(HttpServletRequest.class));

    // then
    assertThat(user).isEqualTo(USER_SUBJECT);
  }

  @Test
  void shouldIgnoreJwtInContextAndFallThroughToCookieWhenFlagDisabled() {
    // given — flag off: a JWT in the context must be ignored and the request must fall through to
    // cookie extraction; with no cookie present the call must ultimately throw
    when(apiConfiguration.isJwtAuthForApiEnabled()).thenReturn(false);
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(buildJwt()));

    // when - then
    assertThatThrownBy(() -> sessionService.getRequestUserOrFailNotAuthorized(emptyRequest()))
        .isInstanceOf(NotAuthorizedException.class);
  }

  @Test
  void shouldThrowNotAuthorizedWhenNeitherBearerTokenNorCookieIsPresent() {
    // given — flag enabled but SecurityContextHolder is empty and the request carries no cookie
    when(apiConfiguration.isJwtAuthForApiEnabled()).thenReturn(true);

    // when - then
    assertThatThrownBy(() -> sessionService.getRequestUserOrFailNotAuthorized(emptyRequest()))
        .isInstanceOf(NotAuthorizedException.class);
  }

  /** Returns a mock request that has no auth cookie and won't NPE inside AuthCookieService. */
  private static HttpServletRequest emptyRequest() {
    final HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getAttributeNames()).thenReturn(Collections.emptyEnumeration());
    return request;
  }

  private static Jwt buildJwt() {
    return Jwt.withTokenValue("test-token")
        .header("alg", "none")
        .claim("sub", USER_SUBJECT)
        .build();
  }
}
