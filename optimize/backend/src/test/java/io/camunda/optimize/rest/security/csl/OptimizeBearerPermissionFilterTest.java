/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.identity.sdk.authentication.exception.TokenVerificationException;
import io.camunda.optimize.rest.exceptions.NotAuthorizedException;
import io.camunda.optimize.service.security.CCSMTokenService;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.api.model.CamundaAuthentication;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class OptimizeBearerPermissionFilterTest {

  private final CamundaAuthenticationProvider authenticationProvider =
      mock(CamundaAuthenticationProvider.class);
  private final CCSMTokenService tokenService = mock(CCSMTokenService.class);
  private final OptimizeBearerPermissionFilter filter =
      new OptimizeBearerPermissionFilter(authenticationProvider, tokenService);
  private final HttpServletRequest request = mock(HttpServletRequest.class);
  private final HttpServletResponse response = mock(HttpServletResponse.class);
  private final FilterChain chain = mock(FilterChain.class);

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  private static Jwt jwtWithClaims(final Map<String, Object> claims) {
    return Jwt.withTokenValue("raw-token-value")
        .header("alg", "none")
        .claims(c -> c.putAll(claims))
        .build();
  }

  private void authenticateWith(final Jwt jwt) {
    final var context = new SecurityContextImpl(new JwtAuthenticationToken(jwt));
    SecurityContextHolder.setContext(context);
  }

  @Test
  void shouldPassThroughWhenAuthenticationIsNotABearerToken() throws Exception {
    // given: no authentication set, e.g. an OAuth2 login session already handled elsewhere
    SecurityContextHolder.clearContext();

    // when
    filter.doFilter(request, response, chain);

    // then
    verify(chain).doFilter(request, response);
    verifyNoInteractions(authenticationProvider, tokenService);
  }

  @Test
  void shouldPassThroughWithoutCheckingPermissionForAClientPrincipal() throws Exception {
    // given
    final Jwt jwt = jwtWithClaims(Map.of("client_id", "optimize-api-client"));
    authenticateWith(jwt);
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(CamundaAuthentication.of(b -> b.clientId("optimize-api-client")));

    // when
    filter.doFilter(request, response, chain);

    // then
    verify(chain).doFilter(request, response);
    verifyNoInteractions(tokenService);
  }

  @Test
  void shouldPassThroughWhenAUserPrincipalHoldsTheOptimizePermission() throws Exception {
    // given
    final Jwt jwt = jwtWithClaims(Map.of("preferred_username", "demo"));
    authenticateWith(jwt);
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(CamundaAuthentication.of(b -> b.user("demo")));

    // when
    filter.doFilter(request, response, chain);

    // then
    verify(tokenService).verifyAccessToken("raw-token-value");
    verify(chain).doFilter(request, response);
  }

  @Test
  void shouldReject401WhenAUserPrincipalLacksTheOptimizePermission() throws Exception {
    // given
    final Jwt jwt = jwtWithClaims(Map.of("preferred_username", "noopt"));
    authenticateWith(jwt);
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(CamundaAuthentication.of(b -> b.user("noopt")));
    doThrow(new NotAuthorizedException("User is not authorized to access Optimize"))
        .when(tokenService)
        .verifyAccessToken("raw-token-value");

    // when
    filter.doFilter(request, response, chain);

    // then
    verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED);
    verifyNoInteractions(chain);
  }

  @Test
  void shouldPassThroughWhenTheTokenCannotBeFreshlyVerified() throws Exception {
    // given
    final Jwt jwt = jwtWithClaims(Map.of("preferred_username", "noopt"));
    authenticateWith(jwt);
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(CamundaAuthentication.of(b -> b.user("noopt")));
    doThrow(new TokenVerificationException("token expired"))
        .when(tokenService)
        .verifyAccessToken("raw-token-value");

    // when
    filter.doFilter(request, response, chain);

    // then
    verify(chain).doFilter(request, response);
  }

  @Test
  void shouldRequireTheCheckWhenThePrincipalCannotBeClassified() throws Exception {
    // given: CSL's own converter throws (e.g. neither username nor client-id claim resolves) —
    // fail closed, exactly as an unclassifiable claims set did before this reused CSL's own
    // classification instead of re-deriving it
    final Jwt jwt = jwtWithClaims(Map.of("sub", "no-mapped-claims"));
    authenticateWith(jwt);
    when(authenticationProvider.getCamundaAuthentication())
        .thenThrow(new IllegalArgumentException("Neither username nor clientId claim resolved"));

    // when
    filter.doFilter(request, response, chain);

    // then
    verify(tokenService).verifyAccessToken("raw-token-value");
    verify(chain).doFilter(request, response);
  }
}
