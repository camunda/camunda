/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import io.camunda.identity.sdk.authentication.exception.TokenVerificationException;
import io.camunda.optimize.rest.exceptions.NotAuthorizedException;
import io.camunda.optimize.service.security.CCSMTokenService;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Unit-level counterpart to {@code CslChainIntegrationTest}'s Bug A scenarios: exercises {@link
 * OptimizeCcsmSessionPermissionEnforcementFilter#doFilterInternal} directly against a mocked {@link
 * CCSMTokenService}, mirroring how {@code OptimizeIdentityPermissionValidatorTest} covers {@link
 * OptimizeIdentityPermissionValidator} — cheaper to extend than only relying on the full chain
 * integration test.
 */
@ExtendWith(MockitoExtension.class)
class OptimizeCcsmSessionPermissionEnforcementFilterTest {

  @Mock private CCSMTokenService ccsmTokenService;

  private OptimizeCcsmSessionPermissionEnforcementFilter filter() {
    return new OptimizeCcsmSessionPermissionEnforcementFilter(ccsmTokenService);
  }

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void shouldPassThroughWhenNoSessionAccessTokenIsPresent() throws Exception {
    // given
    when(ccsmTokenService.getSessionAccessToken(any())).thenReturn(Optional.empty());
    final MockHttpServletRequest request = new MockHttpServletRequest();
    final MockHttpServletResponse response = new MockHttpServletResponse();
    final MockFilterChain chain = new MockFilterChain();

    // when
    filter().doFilterInternal(request, response, chain);

    // then
    assertThat(chain.getRequest())
        .as("no session token: request must reach downstream")
        .isNotNull();
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  void shouldPassThroughWhenSessionAccessTokenIsStillAuthorized() throws Exception {
    // given
    when(ccsmTokenService.getSessionAccessToken(any())).thenReturn(Optional.of("token"));
    // verifyAccessToken("token") stays a no-op (granted) by Mockito default.
    final MockHttpServletRequest request = new MockHttpServletRequest();
    final MockHttpServletResponse response = new MockHttpServletResponse();
    final MockFilterChain chain = new MockFilterChain();

    // when
    filter().doFilterInternal(request, response, chain);

    // then
    assertThat(chain.getRequest())
        .as("still-authorized token: request must reach downstream")
        .isNotNull();
  }

  @Test
  void shouldInvalidateSessionAndRejectWhenTokenNoLongerAuthorized() throws Exception {
    // given
    when(ccsmTokenService.getSessionAccessToken(any())).thenReturn(Optional.of("token"));
    doThrow(new NotAuthorizedException("no longer authorized"))
        .when(ccsmTokenService)
        .verifyAccessToken("token");
    setAuthenticatedSecurityContext();
    final MockHttpServletRequest request = new MockHttpServletRequest();
    request.getSession(true);
    final MockHttpServletResponse response = new MockHttpServletResponse();
    final MockFilterChain chain = new MockFilterChain();

    // when
    filter().doFilterInternal(request, response, chain);

    // then
    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(chain.getRequest()).as("rejected token must not reach downstream").isNull();
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    assertThat(request.getSession(false)).as("session must be invalidated").isNull();
  }

  @Test
  void shouldInvalidateSessionAndRejectWhenTokenCannotBeVerified() throws Exception {
    // given
    // CCSMTokenService#verifyAccessToken throws TokenVerificationException (an IdentityException,
    // not a NotAuthorizedException) directly for an invalid/expired token; the filter must fail
    // closed here too rather than let it propagate as an uncaught 500.
    when(ccsmTokenService.getSessionAccessToken(any())).thenReturn(Optional.of("token"));
    doThrow(new TokenVerificationException("token invalid"))
        .when(ccsmTokenService)
        .verifyAccessToken("token");
    final MockHttpServletRequest request = new MockHttpServletRequest();
    final MockHttpServletResponse response = new MockHttpServletResponse();
    final MockFilterChain chain = new MockFilterChain();

    // when
    filter().doFilterInternal(request, response, chain);

    // then
    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(chain.getRequest()).as("unverifiable token must not reach downstream").isNull();
  }

  @Test
  void shouldInvalidateSessionAndRejectOnUnexpectedError() throws Exception {
    // given
    // A security boundary must fail closed on the unexpected rather than propagate it, mirroring
    // OptimizeIdentityPermissionValidator's final RuntimeException catch-all.
    when(ccsmTokenService.getSessionAccessToken(any())).thenReturn(Optional.of("token"));
    doThrow(new IllegalStateException("unexpected"))
        .when(ccsmTokenService)
        .verifyAccessToken("token");
    final MockHttpServletRequest request = new MockHttpServletRequest();
    final MockHttpServletResponse response = new MockHttpServletResponse();
    final MockFilterChain chain = new MockFilterChain();

    // when
    filter().doFilterInternal(request, response, chain);

    // then
    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(chain.getRequest()).as("unexpected error must not reach downstream").isNull();
  }

  private static void setAuthenticatedSecurityContext() {
    final var authentication = new TestingAuthenticationToken("alice", null, "ROLE_USER");
    authentication.setAuthenticated(true);
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }
}
