/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.ccsm;

import static io.camunda.optimize.rest.constants.RestConstants.OPTIMIZE_REFRESH_TOKEN;

import com.auth0.jwt.exceptions.TokenExpiredException;
import io.camunda.identity.sdk.authentication.AccessToken;
import io.camunda.identity.sdk.authentication.Tokens;
import io.camunda.identity.sdk.exception.IdentityException;
import io.camunda.optimize.rest.exceptions.NotAuthorizedException;
import io.camunda.optimize.service.security.AuthCookieService;
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
import org.springframework.context.annotation.Conditional;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;

@Conditional(CCSMCondition.class)
public class CCSMAuthenticationCookieFilter extends AbstractPreAuthenticatedProcessingFilter {

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
        // Check the validity of the access token
        AuthCookieService.extractJoinedCookieValueFromCookies(
                cookiesByName.values().stream().toList())
            .ifPresentOrElse(
                ccsmTokenService::verifyToken,
                // If no access token cookie is present, we can try renewing the tokens using the
                // refresh token
                () -> tryCookieRenewal(request, response, cookiesByName));
      } catch (final TokenExpiredException expiredException) {
        // If the access token has expired, we try to renew the tokens using the refresh token
        tryCookieRenewal(request, response, cookiesByName);
      } catch (final IdentityException verificationException) {
        // If any renewal fails or the access token is otherwise not valid, try to revoke the tokens
        // using the refresh token
        try {
          Optional.ofNullable(cookiesByName.get(OPTIMIZE_REFRESH_TOKEN))
              .ifPresent(
                  refreshTokenCookie ->
                      ccsmTokenService.revokeToken(refreshTokenCookie.getValue()));
        } catch (final IdentityException ex) {
          // It's possible that the revoking will fail, but we catch it so that we can still delete
          // the cookies
        } finally {
          deleteCookies(response);
        }
      } catch (final NotAuthorizedException notAuthorizedException) {
        // During token verification, it could be that the user is no longer authorized to access
        // Optimize, in which case we delete any existing cookies
        deleteCookies(response);
      }
    }
    super.doFilter(request, response, chain);
  }

  @Override
  protected Object getPreAuthenticatedPrincipal(final HttpServletRequest request) {
    return Optional.ofNullable(request.getCookies())
        .flatMap(
            cookies ->
                AuthCookieService.extractJoinedCookieValueFromCookies(Arrays.asList(cookies))
                    .map(ccsmTokenService::getSubjectFromToken))
        .orElseGet(
            () ->
                AuthCookieService.getAuthCookieToken(request)
                    .flatMap(this::validToken)
                    .map(ccsmTokenService::getSubjectFromToken)
                    .orElse(null));
  }

  @Override
  protected Object getPreAuthenticatedCredentials(final HttpServletRequest request) {
    return Optional.ofNullable(request.getCookies())
        .flatMap(
            cookies ->
                AuthCookieService.extractJoinedCookieValueFromCookies(Arrays.asList(cookies)))
        .orElseGet(() -> AuthCookieService.getAuthCookieToken(request).orElse(null));
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
              // We set the auth token as an attribute on this request so that it can be picked up
              // when extracting the principal and credentials later
              final int maxCookieLength =
                  configurationService.getAuthConfiguration().getCookieConfiguration().getMaxSize();
              final String tokenValue = accessToken.getToken().getToken();
              final int numberOfCookies =
                  (int) Math.ceil((double) tokenValue.length() / maxCookieLength);
              for (int i = 0; i < numberOfCookies; i++) {
                request.setAttribute(
                    AuthCookieService.getAuthorizationCookieNameWithSuffix(i),
                    i == (numberOfCookies - 1)
                        ? tokenValue.substring((i * maxCookieLength))
                        : tokenValue.substring(
                            (i * maxCookieLength), ((i * maxCookieLength) + maxCookieLength)));
              }
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
