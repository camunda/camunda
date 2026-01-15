/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.ccsm;

import static io.camunda.optimize.rest.constants.RestConstants.AUTH_COOKIE_TOKEN_VALUE_PREFIX;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.auth0.jwt.interfaces.DecodedJWT;
import io.camunda.identity.sdk.authentication.AccessToken;
import io.camunda.identity.sdk.authentication.Tokens;
import io.camunda.identity.sdk.authentication.exception.TokenVerificationException;
import io.camunda.optimize.rest.constants.RestConstants;
import io.camunda.optimize.service.security.AuthCookieService;
import io.camunda.optimize.service.security.CCSMTokenService;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.NotAuthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;

@ExtendWith(MockitoExtension.class)
public class CCSMAuthenticationCookieFilterTest {

  @Mock private ConfigurationService configurationService;
  @Mock private CCSMTokenService ccsmTokenService;
  @Mock private AuthenticationManager authenticationManager;

  private CCSMAuthenticationCookieFilter filter;
  private HttpServletRequest mockRequest;
  private HttpServletResponse mockResponse;
  private FilterChain mockFilterChain;

  @BeforeEach
  public void setup() {
    filter =
        new CCSMAuthenticationCookieFilter(
            ccsmTokenService, configurationService, authenticationManager);
    mockRequest = mock(HttpServletRequest.class);
    mockResponse = mock(HttpServletResponse.class);
    mockFilterChain = mock(FilterChain.class);

    // Mock the request to return empty enumeration for getAttributeNames()
    when(mockRequest.getAttributeNames()).thenReturn(java.util.Collections.emptyEnumeration());
  }

  @Test
  public void testVerifyAccessTokenSucceeds() throws Exception {
    // given
    final String validAccessToken = "valid.access.token";
    final Cookie accessTokenCookie =
        new Cookie(
            AuthCookieService.getAuthorizationCookieNameWithSuffix(0),
            AUTH_COOKIE_TOKEN_VALUE_PREFIX + validAccessToken);
    final Cookie[] cookies = new Cookie[] {accessTokenCookie};

    when(mockRequest.getCookies()).thenReturn(cookies);

    // when
    filter.doFilter(mockRequest, mockResponse, mockFilterChain);

    // then
    verify(ccsmTokenService, times(1)).verifyAccessToken(anyString());
    verify(mockFilterChain, times(1)).doFilter(mockRequest, mockResponse);
  }

  @Test
  public void testVerifyAccessTokenFailsNotAuthorized() throws Exception {
    // given
    final String accessToken = "valid.access.token.without.optimize.permission";
    final Cookie accessTokenCookie =
        new Cookie(
            AuthCookieService.getAuthorizationCookieNameWithSuffix(0),
            AUTH_COOKIE_TOKEN_VALUE_PREFIX + accessToken);
    final Cookie[] cookies = new Cookie[] {accessTokenCookie};

    when(mockRequest.getCookies()).thenReturn(cookies);

    // verifyAccessToken throws NotAuthorizedException (user lacks Optimize permissions)
    doThrow(new NotAuthorizedException("User is not authorized to access Optimize"))
        .when(ccsmTokenService)
        .verifyAccessToken(anyString());

    // when
    filter.doFilter(mockRequest, mockResponse, mockFilterChain);

    // then
    verify(ccsmTokenService, times(1)).verifyAccessToken(anyString());
    verify(ccsmTokenService, times(1)).createOptimizeDeleteAuthCookies();
    verify(mockFilterChain, times(1)).doFilter(mockRequest, mockResponse);
  }

  @Test
  public void testVerifyAccessTokenFailsThenRenewsTokenSuccess() throws Exception {
    // given
    final String expiredAccessToken = "expired.access.token";
    final String refreshToken = "refresh.token";
    final Cookie accessTokenCookie =
        new Cookie(
            AuthCookieService.getAuthorizationCookieNameWithSuffix(0),
            AUTH_COOKIE_TOKEN_VALUE_PREFIX + expiredAccessToken);
    final Cookie refreshTokenCookie =
        new Cookie(RestConstants.OPTIMIZE_REFRESH_TOKEN, refreshToken);
    final Cookie[] cookies = new Cookie[] {accessTokenCookie, refreshTokenCookie};

    when(mockRequest.getCookies()).thenReturn(cookies);
    when(mockRequest.getScheme()).thenReturn("https");

    // First call to verifyAccessToken throws TokenVerificationException
    doThrow(new TokenVerificationException("Token verification failed"))
        .when(ccsmTokenService)
        .verifyAccessToken(anyString());

    // Token renewal succeeds - mock the objects needed
    final Tokens mockTokens = mock(Tokens.class);
    final AccessToken mockAccessToken = mock(AccessToken.class);
    final DecodedJWT mockInnerToken = mock(DecodedJWT.class);

    when(ccsmTokenService.renewToken(refreshToken)).thenReturn(mockTokens);
    when(mockTokens.getAccessToken()).thenReturn("new.access.token");
    when(ccsmTokenService.verifyToken("new.access.token")).thenReturn(mockAccessToken);
    when(mockAccessToken.getToken()).thenReturn(mockInnerToken);
    when(mockInnerToken.getToken()).thenReturn("new.access.token");
    when(configurationService.getAuthConfiguration())
        .thenReturn(
            mock(io.camunda.optimize.service.util.configuration.security.AuthConfiguration.class));
    when(configurationService.getAuthConfiguration().getCookieConfiguration())
        .thenReturn(
            mock(
                io.camunda.optimize.service.util.configuration.security.CookieConfiguration.class));
    when(configurationService.getAuthConfiguration().getCookieConfiguration().getMaxSize())
        .thenReturn(4096);
    when(ccsmTokenService.createOptimizeAuthCookies(mockTokens, mockAccessToken, "https"))
        .thenReturn(java.util.Collections.emptyList());

    // when
    filter.doFilter(mockRequest, mockResponse, mockFilterChain);

    // then
    verify(ccsmTokenService, times(1)).verifyAccessToken(anyString());
    verify(ccsmTokenService, times(1)).renewToken(refreshToken);
    verify(ccsmTokenService, times(1)).verifyToken("new.access.token");
    verify(ccsmTokenService, times(1))
        .createOptimizeAuthCookies(mockTokens, mockAccessToken, "https");
    verify(mockFilterChain, times(1)).doFilter(mockRequest, mockResponse);
  }

  @Test
  public void testVerifyAccessTokenFailThenRenewTokenFail() throws Exception {
    // given
    final String expiredAccessToken = "expired.access.token";
    final String refreshToken = "refresh.token";
    final Cookie accessTokenCookie =
        new Cookie(
            AuthCookieService.getAuthorizationCookieNameWithSuffix(0),
            AUTH_COOKIE_TOKEN_VALUE_PREFIX + expiredAccessToken);
    final Cookie refreshTokenCookie =
        new Cookie(RestConstants.OPTIMIZE_REFRESH_TOKEN, refreshToken);
    final Cookie[] cookies = new Cookie[] {accessTokenCookie, refreshTokenCookie};

    when(mockRequest.getCookies()).thenReturn(cookies);

    // First call to verifyAccessToken throws TokenVerificationException
    doThrow(new TokenVerificationException("Token verification failed"))
        .when(ccsmTokenService)
        .verifyAccessToken(anyString());

    // Token renewal fails with NotAuthorizedException
    when(ccsmTokenService.renewToken(refreshToken))
        .thenThrow(new NotAuthorizedException("Token could not be renewed"));

    // when
    filter.doFilter(mockRequest, mockResponse, mockFilterChain);

    // then
    verify(ccsmTokenService, times(1)).verifyAccessToken(anyString());
    verify(ccsmTokenService, times(1)).renewToken(refreshToken);
    verify(ccsmTokenService, times(1)).createOptimizeDeleteAuthCookies();
    verify(mockFilterChain, times(1)).doFilter(mockRequest, mockResponse);
  }

  @Test
  public void testVerifyAccessTokenFailThenRenewTokenSuccessThenVerifyTokenFail() throws Exception {
    // given
    final String expiredAccessToken = "expired.access.token";
    final String refreshToken = "refresh.token";
    final Cookie accessTokenCookie =
        new Cookie(
            AuthCookieService.getAuthorizationCookieNameWithSuffix(0),
            AUTH_COOKIE_TOKEN_VALUE_PREFIX + expiredAccessToken);
    final Cookie refreshTokenCookie =
        new Cookie(RestConstants.OPTIMIZE_REFRESH_TOKEN, refreshToken);
    final Cookie[] cookies = new Cookie[] {accessTokenCookie, refreshTokenCookie};

    when(mockRequest.getCookies()).thenReturn(cookies);

    // First call to verifyAccessToken throws TokenVerificationException
    doThrow(new TokenVerificationException("Token verification failed"))
        .when(ccsmTokenService)
        .verifyAccessToken(anyString());

    // Token renewal succeeds
    final Tokens mockTokens = mock(Tokens.class);
    when(ccsmTokenService.renewToken(refreshToken)).thenReturn(mockTokens);
    when(mockTokens.getAccessToken()).thenReturn("new.access.token");

    // But verifyToken throws NotAuthorizedException (user lacks Optimize permissions)
    when(ccsmTokenService.verifyToken("new.access.token"))
        .thenThrow(new NotAuthorizedException("User is not authorized to access Optimize"));

    // when
    filter.doFilter(mockRequest, mockResponse, mockFilterChain);

    // then
    verify(ccsmTokenService, times(1)).verifyAccessToken(anyString());
    verify(ccsmTokenService, times(1)).renewToken(refreshToken);
    verify(ccsmTokenService, times(1)).verifyToken("new.access.token");
    verify(ccsmTokenService, times(1)).createOptimizeDeleteAuthCookies();
    verify(mockFilterChain, times(1)).doFilter(mockRequest, mockResponse);
  }

  @Test
  public void testNoAccessTokenThenRenewTokenSuccess() throws Exception {
    // given - only refresh token cookie, no access token cookie
    final String refreshToken = "refresh.token";
    final Cookie refreshTokenCookie =
        new Cookie(RestConstants.OPTIMIZE_REFRESH_TOKEN, refreshToken);
    final Cookie[] cookies = new Cookie[] {refreshTokenCookie};

    when(mockRequest.getCookies()).thenReturn(cookies);
    when(mockRequest.getScheme()).thenReturn("https");

    // Token renewal succeeds - mock the objects needed
    final Tokens mockTokens = mock(Tokens.class);
    final AccessToken mockAccessToken = mock(AccessToken.class);
    final DecodedJWT mockInnerToken = mock(DecodedJWT.class);

    when(ccsmTokenService.renewToken(refreshToken)).thenReturn(mockTokens);
    when(mockTokens.getAccessToken()).thenReturn("new.access.token");
    when(ccsmTokenService.verifyToken("new.access.token")).thenReturn(mockAccessToken);
    when(mockAccessToken.getToken()).thenReturn(mockInnerToken);
    when(mockInnerToken.getToken()).thenReturn("new.access.token");
    when(configurationService.getAuthConfiguration())
        .thenReturn(
            mock(io.camunda.optimize.service.util.configuration.security.AuthConfiguration.class));
    when(configurationService.getAuthConfiguration().getCookieConfiguration())
        .thenReturn(
            mock(
                io.camunda.optimize.service.util.configuration.security.CookieConfiguration.class));
    when(configurationService.getAuthConfiguration().getCookieConfiguration().getMaxSize())
        .thenReturn(4096);
    when(ccsmTokenService.createOptimizeAuthCookies(mockTokens, mockAccessToken, "https"))
        .thenReturn(java.util.Collections.emptyList());

    // when
    filter.doFilter(mockRequest, mockResponse, mockFilterChain);

    // then
    verify(ccsmTokenService, times(1)).renewToken(refreshToken);
    verify(ccsmTokenService, times(1)).verifyToken("new.access.token");
    verify(ccsmTokenService, times(1))
        .createOptimizeAuthCookies(mockTokens, mockAccessToken, "https");
    verify(mockFilterChain, times(1)).doFilter(mockRequest, mockResponse);
  }

  @Test
  public void testNoAccessTokenThenRenewTokenFail() throws Exception {
    // given - only refresh token cookie, no access token cookie
    final String refreshToken = "refresh.token";
    final Cookie refreshTokenCookie =
        new Cookie(RestConstants.OPTIMIZE_REFRESH_TOKEN, refreshToken);
    final Cookie[] cookies = new Cookie[] {refreshTokenCookie};

    when(mockRequest.getCookies()).thenReturn(cookies);

    // Token renewal fails with NotAuthorizedException
    when(ccsmTokenService.renewToken(refreshToken))
        .thenThrow(new NotAuthorizedException("Token could not be renewed"));

    // when
    filter.doFilter(mockRequest, mockResponse, mockFilterChain);

    // then
    verify(ccsmTokenService, times(1)).renewToken(refreshToken);
    verify(ccsmTokenService, times(1)).createOptimizeDeleteAuthCookies();
    verify(mockFilterChain, times(1)).doFilter(mockRequest, mockResponse);
  }
}
