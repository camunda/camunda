/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import io.camunda.identity.sdk.authentication.exception.TokenVerificationException;
import io.camunda.optimize.rest.exceptions.NotAuthorizedException;
import io.camunda.optimize.service.security.CCSMTokenService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

/**
 * Unit-level counterpart to {@code CslChainIntegrationTest}'s Bug A scenarios, cheaper to extend
 * than the full chain test.
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
    // A bearer-only or anonymous request, which this filter leaves to CSL.
    when(ccsmTokenService.getSessionAccessToken(any())).thenReturn(Optional.empty());
    final MockHttpServletRequest request = apiRequest();
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
  void shouldRejectButKeepSessionWhenSessionHoldsNoAccessToken() {
    // given
    // A session whose authorized client is gone resolves no token either, and CSL would then serve
    // the request from the id_token claims. Only a missing OAuth2AuthenticationToken means "not a
    // session request", so this case must fail closed.
    when(ccsmTokenService.getSessionAccessToken(any())).thenReturn(Optional.empty());
    setOAuth2AuthenticatedSecurityContext();
    final MockHttpServletRequest request = apiRequest();
    request.getSession(true);
    final MockHttpServletResponse response = new MockHttpServletResponse();
    final MockFilterChain chain = new MockFilterChain();

    // when
    final ThrowingCallable doFilter = () -> filter().doFilterInternal(request, response, chain);

    // then
    assertThatThrownBy(doFilter).isInstanceOf(AuthenticationException.class);
    assertThat(chain.getRequest()).as("session without a token must not reach downstream").isNull();
    assertThat(request.getSession(false)).as("session must survive").isNotNull();
  }

  @Test
  void shouldPassThroughWhenSessionAccessTokenIsStillAuthorized() throws Exception {
    // given
    when(ccsmTokenService.getSessionAccessToken(any())).thenReturn(Optional.of("token"));
    // verifyAccessToken("token") stays a no-op (granted) by Mockito default.
    final MockHttpServletRequest request = apiRequest();
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
  void shouldPassThroughWithoutVerifyingWhenSessionAccessTokenIsExpired() throws Exception {
    // given
    // Only CSL's webapp chain refreshes the token, so on the API chain it would stay expired for
    // the rest of the session.
    final String expiredToken = jwtExpiringAt(Instant.now().minusSeconds(60));
    when(ccsmTokenService.getSessionAccessToken(any())).thenReturn(Optional.of(expiredToken));
    final MockHttpServletRequest request = apiRequest();
    final MockHttpServletResponse response = new MockHttpServletResponse();
    final MockFilterChain chain = new MockFilterChain();

    // when
    filter().doFilterInternal(request, response, chain);

    // then
    assertThat(chain.getRequest()).as("expired token: request must reach downstream").isNotNull();
    verify(ccsmTokenService, never()).verifyAccessToken(any());
  }

  @Test
  void shouldVerifyWhenSessionAccessTokenIsNotExpiredYet() throws Exception {
    // given
    final String validToken = jwtExpiringAt(Instant.now().plusSeconds(60));
    when(ccsmTokenService.getSessionAccessToken(any())).thenReturn(Optional.of(validToken));
    doThrow(new NotAuthorizedException("no longer authorized"))
        .when(ccsmTokenService)
        .verifyAccessToken(validToken);
    final MockHttpServletRequest request = apiRequest();
    final MockHttpServletResponse response = new MockHttpServletResponse();
    final MockFilterChain chain = new MockFilterChain();

    // when
    final ThrowingCallable doFilter = () -> filter().doFilterInternal(request, response, chain);

    // then
    assertThatThrownBy(doFilter).isInstanceOf(AuthenticationException.class);
    assertThat(chain.getRequest()).as("rejected token must not reach downstream").isNull();
  }

  @Test
  void shouldInvalidateSessionAndRejectWhenTokenNoLongerAuthorized() {
    // given
    when(ccsmTokenService.getSessionAccessToken(any())).thenReturn(Optional.of("token"));
    doThrow(new NotAuthorizedException("no longer authorized"))
        .when(ccsmTokenService)
        .verifyAccessToken("token");
    setAuthenticatedSecurityContext();
    final MockHttpServletRequest request = apiRequest();
    request.getSession(true);
    final MockHttpServletResponse response = new MockHttpServletResponse();
    final MockFilterChain chain = new MockFilterChain();

    // when
    final ThrowingCallable doFilter = () -> filter().doFilterInternal(request, response, chain);

    // then
    // The exception is the denial. CslChainIntegrationTest covers the 401 it becomes.
    assertThatThrownBy(doFilter).isInstanceOf(AuthenticationException.class);
    assertThat(chain.getRequest()).as("rejected token must not reach downstream").isNull();
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    assertThat(request.getSession(false)).as("session must be invalidated").isNull();
  }

  @Test
  void shouldDenyWebappRequestWithTerminalForbidden() throws Exception {
    // given
    // An AuthenticationException on a navigation would restart the login, and the IdP's live
    // session re-issues a code right away, so the user would loop.
    when(ccsmTokenService.getSessionAccessToken(any())).thenReturn(Optional.of("token"));
    doThrow(new NotAuthorizedException("no longer authorized"))
        .when(ccsmTokenService)
        .verifyAccessToken("token");
    setAuthenticatedSecurityContext();
    final MockHttpServletRequest request = webappRequest();
    request.getSession(true);
    final MockHttpServletResponse response = new MockHttpServletResponse();
    final MockFilterChain chain = new MockFilterChain();

    // when
    filter().doFilterInternal(request, response, chain);

    // then
    assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
    assertThat(chain.getRequest()).as("rejected token must not reach downstream").isNull();
    assertThat(request.getSession(false)).as("session must be invalidated").isNull();
  }

  @Test
  void shouldDenyWebappRequestWithTerminalForbiddenWhenSessionHoldsNoAccessToken()
      throws Exception {
    // given
    when(ccsmTokenService.getSessionAccessToken(any())).thenReturn(Optional.empty());
    setOAuth2AuthenticatedSecurityContext();
    final MockHttpServletRequest request = webappRequest();
    final MockHttpServletResponse response = new MockHttpServletResponse();
    final MockFilterChain chain = new MockFilterChain();

    // when
    filter().doFilterInternal(request, response, chain);

    // then
    assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
    assertThat(chain.getRequest()).as("session without a token must not reach downstream").isNull();
  }

  @Test
  void shouldRejectButKeepSessionWhenTokenCannotBeVerified() {
    // given
    // verifyAccessToken raises TokenVerificationException, not NotAuthorizedException, for an
    // invalid token and when Identity is unreachable. That says nothing about the user's
    // permission, so an Identity outage must not log everybody out.
    when(ccsmTokenService.getSessionAccessToken(any())).thenReturn(Optional.of("token"));
    doThrow(new TokenVerificationException("token invalid"))
        .when(ccsmTokenService)
        .verifyAccessToken("token");
    setAuthenticatedSecurityContext();
    final MockHttpServletRequest request = apiRequest();
    request.getSession(true);
    final MockHttpServletResponse response = new MockHttpServletResponse();
    final MockFilterChain chain = new MockFilterChain();

    // when
    final ThrowingCallable doFilter = () -> filter().doFilterInternal(request, response, chain);

    // then
    assertThatThrownBy(doFilter).isInstanceOf(AuthenticationException.class);
    assertThat(chain.getRequest()).as("unverifiable token must not reach downstream").isNull();
    assertThat(request.getSession(false)).as("session must survive").isNotNull();
  }

  @Test
  void shouldRejectButKeepSessionOnUnexpectedError() {
    // given
    // Fail closed rather than propagate. As with IdentityException, the session is not at fault.
    when(ccsmTokenService.getSessionAccessToken(any())).thenReturn(Optional.of("token"));
    doThrow(new IllegalStateException("unexpected"))
        .when(ccsmTokenService)
        .verifyAccessToken("token");
    setAuthenticatedSecurityContext();
    final MockHttpServletRequest request = apiRequest();
    request.getSession(true);
    final MockHttpServletResponse response = new MockHttpServletResponse();
    final MockFilterChain chain = new MockFilterChain();

    // when
    final ThrowingCallable doFilter = () -> filter().doFilterInternal(request, response, chain);

    // then
    assertThatThrownBy(doFilter).isInstanceOf(AuthenticationException.class);
    assertThat(chain.getRequest()).as("unexpected error must not reach downstream").isNull();
    assertThat(request.getSession(false)).as("session must survive").isNotNull();
  }

  private static MockHttpServletRequest apiRequest() {
    return new MockHttpServletRequest("GET", "/api/definition");
  }

  private static MockHttpServletRequest webappRequest() {
    return new MockHttpServletRequest("GET", "/dashboards");
  }

  private static String jwtExpiringAt(final Instant expiresAt) {
    // The filter only reads the exp claim, CCSMTokenService validates the token.
    return JWT.create().withExpiresAt(expiresAt).sign(Algorithm.none());
  }

  private static void setOAuth2AuthenticatedSecurityContext() {
    // An OAuth2AuthenticationToken is what distinguishes a session request from a bearer-only one.
    final var authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
    final var user = new DefaultOAuth2User(authorities, Map.of("sub", "alice"), "sub");
    SecurityContextHolder.getContext()
        .setAuthentication(new OAuth2AuthenticationToken(user, authorities, "camunda"));
  }

  private static void setAuthenticatedSecurityContext() {
    final var authentication = new TestingAuthenticationToken("alice", null, "ROLE_USER");
    authentication.setAuthenticated(true);
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }
}
