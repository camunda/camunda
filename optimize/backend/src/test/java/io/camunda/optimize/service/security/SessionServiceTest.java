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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import io.camunda.optimize.rest.exceptions.NotAuthorizedException;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.OptimizeApiConfiguration;
import io.camunda.optimize.service.util.configuration.security.AuthConfiguration;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.api.model.CamundaAuthentication;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

  private static final String USER_SUBJECT = "user123";
  private static final String CSL_USERNAME = "auth0|csl-user";
  private static final String OTHER_USER_SUBJECT = "someone-else";

  @Mock private TerminatedSessionService terminatedSessionService;
  @Mock private ConfigurationService configurationService;
  @Mock private AuthConfiguration authConfiguration;
  @Mock private OptimizeApiConfiguration apiConfiguration;
  @Mock private CamundaAuthenticationProvider camundaAuthenticationProvider;

  private SessionService sessionService;

  @BeforeEach
  void setUp() {
    lenient().when(authConfiguration.getTokenSecret()).thenReturn(Optional.empty());
    lenient().when(configurationService.getAuthConfiguration()).thenReturn(authConfiguration);
    lenient().when(configurationService.getOptimizeApiConfiguration()).thenReturn(apiConfiguration);
    // Legacy setup by default: no CamundaAuthenticationProvider bean exists.
    sessionService = sessionServiceWith(noCamundaAuthenticationProvider());
  }

  private SessionService sessionServiceWith(
      final ObjectProvider<CamundaAuthenticationProvider> provider) {
    return new SessionService(terminatedSessionService, configurationService, provider);
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

  @Test
  void shouldReturnCslAuthenticatedUsernameForAnOauth2Session() {
    // given — CSL mode: an oauth2Login session, no Optimize auth cookie
    sessionService = sessionServiceWith(camundaAuthenticationProviderOf(CSL_USERNAME));
    SecurityContextHolder.getContext().setAuthentication(oauth2Session());

    // when — CSL resolves first, so the cookie path is never reached
    final String user =
        sessionService.getRequestUserOrFailNotAuthorized(mock(HttpServletRequest.class));

    // then
    assertThat(user).isEqualTo(CSL_USERNAME);
  }

  @Test
  void shouldFailWhenCslResolvesNoUsernameForAnOauth2Session() {
    // given — provider present but no authenticated username to offer
    sessionService = sessionServiceWith(camundaAuthenticationProviderOf(null));
    SecurityContextHolder.getContext().setAuthentication(oauth2Session());

    // when - then
    assertThatThrownBy(
            () -> sessionService.getRequestUserOrFailNotAuthorized(mock(HttpServletRequest.class)))
        .isInstanceOf(NotAuthorizedException.class);
  }

  @Test
  void shouldNotFallBackToTheLegacyCookieForACslSession() {
    // given — a CSL session that resolves no username, plus a leftover Optimize auth cookie naming
    // a different user. That cookie's subject is decoded without verifying the signature, and under
    // CSL no filter validates it, so it must never be used to attribute the request.
    sessionService = sessionServiceWith(camundaAuthenticationProviderOf(null));
    SecurityContextHolder.getContext().setAuthentication(oauth2Session());

    // when - then
    assertThatThrownBy(
            () -> sessionService.getRequestUserOrFailNotAuthorized(requestWithAuthCookie()))
        .isInstanceOf(NotAuthorizedException.class);
  }

  /** A request carrying a leftover Optimize auth cookie whose subject is a different user. */
  private static HttpServletRequest requestWithAuthCookie() {
    final HttpServletRequest request = mock(HttpServletRequest.class);
    lenient().when(request.getAttributeNames()).thenReturn(Collections.emptyEnumeration());
    lenient()
        .when(request.getCookies())
        .thenReturn(
            new Cookie[] {
              new Cookie(
                  AuthCookieService.getAuthorizationCookieNameWithSuffix(0),
                  AuthCookieService.createOptimizeAuthCookieValue(
                      JWT.create().withSubject(OTHER_USER_SUBJECT).sign(Algorithm.none())))
            });
    return request;
  }

  @Test
  void shouldResolveABearerAuthenticatedRequestThroughCsl() {
    // given — CSL present and the context holds a bearer JWT from the CSL API chain. CSL has a
    // converter for that token type, and its username follows the configured username-claim, so it
    // is authoritative over the raw sub and over the legacy jwtAuthForApiEnabled flag.
    sessionService = sessionServiceWith(camundaAuthenticationProviderOf(CSL_USERNAME));
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(buildJwt()));

    // when
    final String user =
        sessionService.getRequestUserOrFailNotAuthorized(mock(HttpServletRequest.class));

    // then
    assertThat(user).isEqualTo(CSL_USERNAME);
  }

  @Test
  void shouldStillUseTheBearerSubjectWhenCslIsAbsent() {
    // given — legacy setup: no CamundaAuthenticationProvider bean, so the pre-CSL behaviour of
    // taking the subject from the bearer token must be preserved
    when(apiConfiguration.isJwtAuthForApiEnabled()).thenReturn(true);
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(buildJwt()));

    final String user =
        sessionService.getRequestUserOrFailNotAuthorized(mock(HttpServletRequest.class));

    assertThat(user).isEqualTo(USER_SUBJECT);
  }

  private ObjectProvider<CamundaAuthenticationProvider> camundaAuthenticationProviderOf(
      final String username) {
    final CamundaAuthentication authentication = mock(CamundaAuthentication.class);
    lenient().when(authentication.authenticatedUsername()).thenReturn(username);
    lenient()
        .when(camundaAuthenticationProvider.getCamundaAuthentication())
        .thenReturn(authentication);
    return singletonProvider(camundaAuthenticationProvider);
  }

  private static ObjectProvider<CamundaAuthenticationProvider> noCamundaAuthenticationProvider() {
    return singletonProvider(null);
  }

  private static ObjectProvider<CamundaAuthenticationProvider> singletonProvider(
      final CamundaAuthenticationProvider provider) {
    return new ObjectProvider<>() {
      @Override
      public CamundaAuthenticationProvider getObject() {
        return provider;
      }

      @Override
      public CamundaAuthenticationProvider getObject(final Object... args) {
        return provider;
      }

      @Override
      public CamundaAuthenticationProvider getIfAvailable() {
        return provider;
      }

      @Override
      public CamundaAuthenticationProvider getIfUnique() {
        return provider;
      }
    };
  }

  private static OAuth2AuthenticationToken oauth2Session() {
    final OidcIdToken idToken =
        new OidcIdToken(
            "id-token", Instant.now(), Instant.now().plusSeconds(300), Map.of("sub", CSL_USERNAME));
    return new OAuth2AuthenticationToken(
        new DefaultOidcUser(AuthorityUtils.createAuthorityList("ROLE_USER"), idToken),
        List.of(),
        "oidc");
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
