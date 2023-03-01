/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.security.ccsm;

import com.auth0.jwt.exceptions.TokenExpiredException;
import io.camunda.identity.sdk.authentication.AccessToken;
import io.camunda.identity.sdk.authentication.Tokens;
import io.camunda.identity.sdk.exception.IdentityException;
import lombok.AllArgsConstructor;
import org.camunda.optimize.service.security.AuthCookieService;
import org.camunda.optimize.service.security.CCSMTokenService;
import org.camunda.optimize.service.util.configuration.condition.CCSMCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.NotAuthorizedException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.camunda.optimize.rest.constants.RestConstants.OPTIMIZE_AUTHORIZATION;
import static org.camunda.optimize.rest.constants.RestConstants.OPTIMIZE_REFRESH_TOKEN;

@Conditional(CCSMCondition.class)
@AllArgsConstructor
public class CCSMAuthenticationCookieFilter extends AbstractPreAuthenticatedProcessingFilter {

  private final CCSMTokenService ccsmTokenService;

  public CCSMAuthenticationCookieFilter(final CCSMTokenService ccsmTokenService,
                                        final AuthenticationManager authenticationManager) {
    this.ccsmTokenService = ccsmTokenService;
    setAuthenticationManager(authenticationManager);
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
    throws IOException, ServletException {
    final Cookie[] cookies = ((HttpServletRequest) request).getCookies();
    if (cookies != null) {
      final Map<String, Cookie> cookiesByName = Arrays.stream(cookies)
        .collect(Collectors.toMap(Cookie::getName, Function.identity()));
      try {
        // Check the validity of the access token
        Optional.ofNullable(cookiesByName.get(OPTIMIZE_AUTHORIZATION))
          .ifPresentOrElse(
            accessTokenCookie -> ccsmTokenService.verifyToken(accessTokenCookie.getValue()),
            // If no access token cookie is present, we can try renewing the tokens using the refresh token
            () -> tryCookieRenewal(request, response, cookiesByName)
          );
      } catch (TokenExpiredException expiredException) {
        // If the access token has expired, we try to renew the tokens using the refresh token
        tryCookieRenewal(request, response, cookiesByName);
      } catch (IdentityException verificationException) {
        // If any renewal fails or the access token is otherwise not valid, try to revoke the tokens using the refresh token
        try {
          Optional.ofNullable(cookiesByName.get(OPTIMIZE_REFRESH_TOKEN))
            .ifPresent(refreshTokenCookie -> ccsmTokenService.revokeToken(refreshTokenCookie.getValue()));
        } catch (IdentityException ex) {
          // It's possible that the revoking will fail, but we catch it so that we can still delete the cookies
        } finally {
          deleteCookies(response);
        }
      } catch (NotAuthorizedException notAuthorizedException) {
        // During token verification, it could be that the user is no longer authorized to access Optimize, in which
        // case we delete any existing cookies
        deleteCookies(response);
      }
    }
    super.doFilter(request, response, chain);
  }

  @Override
  protected Object getPreAuthenticatedPrincipal(final HttpServletRequest request) {
    return Optional.ofNullable(request.getCookies())
      .flatMap(cookies -> Arrays.stream(request.getCookies())
        .filter(cookie -> cookie.getName().equals(OPTIMIZE_AUTHORIZATION))
        .findFirst()
        .map(accessToken -> ccsmTokenService.getSubjectFromToken(accessToken.getValue())))
      .orElseGet(() -> AuthCookieService.getAuthCookieToken(request).map(ccsmTokenService::getSubjectFromToken).orElse(null));
  }

  @Override
  protected Object getPreAuthenticatedCredentials(final HttpServletRequest request) {
    return Optional.ofNullable(request.getCookies())
      .flatMap(cookies -> Arrays.stream(request.getCookies())
        .filter(cookie -> cookie.getName().equals(OPTIMIZE_AUTHORIZATION))
        .findFirst()
        .map(Cookie::getValue))
      .orElseGet(() -> AuthCookieService.getAuthCookieToken(request).orElse(null));
  }

  private void tryCookieRenewal(final ServletRequest request, final ServletResponse response,
                                final Map<String, Cookie> cookiesByName) {
    Optional.ofNullable(cookiesByName.get(OPTIMIZE_REFRESH_TOKEN))
      .ifPresent(refreshTokenCookie -> {
        final Tokens tokens = ccsmTokenService.renewToken(refreshTokenCookie.getValue());
        final AccessToken accessToken = ccsmTokenService.verifyToken(tokens.getAccessToken());
        // We set the auth token as an attribute on this request so that it can be picked up when extracting the principal
        // and credentials later
        request.setAttribute(OPTIMIZE_AUTHORIZATION, accessToken.getToken().getToken());
        ccsmTokenService.createOptimizeAuthCookies(tokens, accessToken, request.getScheme())
          .forEach(((HttpServletResponse) response)::addCookie);
      });
  }

  private void deleteCookies(final ServletResponse response) {
    ccsmTokenService.createOptimizeDeleteAuthCookies().forEach(((HttpServletResponse) response)::addCookie);
  }

}
