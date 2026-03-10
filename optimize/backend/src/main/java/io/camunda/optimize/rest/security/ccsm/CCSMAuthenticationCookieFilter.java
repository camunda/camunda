/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.ccsm;

import static io.camunda.optimize.rest.constants.RestConstants.OPTIMIZE_REFRESH_TOKEN;

import io.camunda.auth.spring.session.ChunkedCookieService;
import io.camunda.identity.sdk.authentication.AccessToken;
import io.camunda.identity.sdk.authentication.Tokens;
import io.camunda.identity.sdk.authentication.exception.TokenVerificationException;
import io.camunda.optimize.rest.exceptions.NotAuthorizedException;
import io.camunda.optimize.service.security.CCSMTokenService;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.CCSMCondition;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;

@Conditional(CCSMCondition.class)
public class CCSMAuthenticationCookieFilter extends AbstractPreAuthenticatedProcessingFilter {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(CCSMAuthenticationCookieFilter.class);

  /**
   * Request attribute key used to pass a renewed access token within a single request. When token
   * renewal occurs during {@link #doFilter}, the renewed token is stored as a request attribute so
   * that {@link #getPreAuthenticatedPrincipal} can pick it up instead of reading the stale cookie.
   */
  private static final String RENEWED_TOKEN_ATTR = "io.camunda.optimize.renewedAccessToken";

  private final ConfigurationService configurationService;
  private final CCSMTokenService ccsmTokenService;

  public CCSMAuthenticationCookieFilter(
      final ConfigurationService configurationService,
      final CCSMTokenService ccsmTokenService,
      final AuthenticationManager authenticationManager) {
    this.configurationService = configurationService;
    this.ccsmTokenService = ccsmTokenService;
    setAuthenticationManager(authenticationManager);
  }

  public CCSMAuthenticationCookieFilter(
      final ConfigurationService configurationService, final CCSMTokenService ccsmTokenService) {
    this.configurationService = configurationService;
    this.ccsmTokenService = ccsmTokenService;
  }

  @Override
  public void doFilter(
      final ServletRequest request, final ServletResponse response, final FilterChain chain)
      throws IOException, ServletException {
    final Cookie[] cookies = ((HttpServletRequest) request).getCookies();
    if (cookies != null) {
      final Map<String, Cookie> cookiesByName =
          Arrays.stream(cookies).collect(Collectors.toMap(Cookie::getName, Function.identity()));
      try {
        try {
          // Check the validity of the access token
          final ChunkedCookieService chunkedCookieService =
              ccsmTokenService.getChunkedCookieService();
          final String token = chunkedCookieService.extractToken((HttpServletRequest) request);
          if (token != null) {
            ccsmTokenService.verifyAccessToken(token);
          } else {
            // If no access token cookie is present, we can try renewing the tokens using the
            // refresh token
            tryCookieRenewal(request, response, cookiesByName);
          }
        } catch (final TokenVerificationException verificationException) {
          // If the access token has any verification exception
          // we try to renew the tokens using the refresh token
          tryCookieRenewal(request, response, cookiesByName);
        }
      } catch (final NotAuthorizedException notAuthorizedException) {
        // During token verification, it could be that the user is no longer authorized to access
        // Optimize, in which case we delete any existing cookies
        LOGGER.debug(notAuthorizedException.getMessage());
        deleteCookies(response);
      }
    }
    super.doFilter(request, response, chain);
  }

  @Override
  protected Object getPreAuthenticatedPrincipal(final HttpServletRequest request) {
    // Check for a renewed token stored as a request attribute during this request
    final String renewedToken = (String) request.getAttribute(RENEWED_TOKEN_ATTR);
    if (renewedToken != null) {
      return validToken(renewedToken).map(ccsmTokenService::getSubjectFromToken).orElse(null);
    }

    // Otherwise, extract the token from cookies
    final String token = ccsmTokenService.getChunkedCookieService().extractToken(request);
    if (token != null) {
      return validToken(token).map(ccsmTokenService::getSubjectFromToken).orElse(null);
    }
    return null;
  }

  @Override
  protected Object getPreAuthenticatedCredentials(final HttpServletRequest request) {
    final String renewedToken = (String) request.getAttribute(RENEWED_TOKEN_ATTR);
    if (renewedToken != null) {
      return renewedToken;
    }
    return ccsmTokenService.getChunkedCookieService().extractToken(request);
  }

  private Optional<String> validToken(final String token) {
    try {
      ccsmTokenService.verifyToken(token);
    } catch (final Exception e) {
      return Optional.empty();
    }
    return Optional.ofNullable(token);
  }

  private void tryCookieRenewal(
      final ServletRequest request,
      final ServletResponse response,
      final Map<String, Cookie> cookiesByName) {
    Optional.ofNullable(cookiesByName.get(OPTIMIZE_REFRESH_TOKEN))
        .ifPresent(
            refreshTokenCookie -> {
              final Tokens tokens = ccsmTokenService.renewToken(refreshTokenCookie.getValue());
              final AccessToken accessToken = ccsmTokenService.verifyToken(tokens.getAccessToken());
              final String tokenValue = accessToken.getToken().getToken();

              // Store the renewed token as a request attribute so getPreAuthenticatedPrincipal
              // can pick it up
              request.setAttribute(RENEWED_TOKEN_ATTR, tokenValue);

              ccsmTokenService
                  .createOptimizeAuthNewCookies(tokens, accessToken, request.getScheme())
                  .forEach(((HttpServletResponse) response)::addCookie);
            });
  }

  private void deleteCookies(final ServletResponse response) {
    ccsmTokenService
        .createOptimizeDeleteAuthCookies()
        .forEach(((HttpServletResponse) response)::addCookie);
  }
}
