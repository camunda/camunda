/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.security;

import static io.camunda.optimize.rest.AuthenticationRestService.AUTHENTICATION_PATH;
import static io.camunda.optimize.rest.AuthenticationRestService.CALLBACK;
import static io.camunda.optimize.rest.constants.RestConstants.AUTH_COOKIE_TOKEN_VALUE_PREFIX;
import static io.camunda.optimize.rest.constants.RestConstants.OPTIMIZE_REFRESH_TOKEN;
import static io.camunda.optimize.service.db.DatabaseConstants.ZEEBE_DATA_SOURCE;
import static io.camunda.optimize.tomcat.OptimizeResourceConstants.REST_API_PATH;

import com.auth0.jwt.interfaces.DecodedJWT;
import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.authentication.AccessToken;
import io.camunda.identity.sdk.authentication.Authentication;
import io.camunda.identity.sdk.authentication.Tokens;
import io.camunda.identity.sdk.authentication.UserDetails;
import io.camunda.identity.sdk.authentication.dto.AuthCodeDto;
import io.camunda.identity.sdk.authentication.exception.CodeExchangeException;
import io.camunda.identity.sdk.authentication.exception.TokenDecodeException;
import io.camunda.identity.sdk.authentication.exception.TokenVerificationException;
import io.camunda.identity.sdk.exception.IdentityException;
import io.camunda.identity.sdk.impl.rest.exception.RestException;
import io.camunda.optimize.dto.optimize.TenantDto;
import io.camunda.optimize.dto.optimize.UserDto;
import io.camunda.optimize.rest.exceptions.NotAuthorizedException;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.CCSMCondition;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
@Conditional(CCSMCondition.class)
public class CCSMTokenService {

  // In Identity, Optimize requires users to have write access to everything
  private static final String OPTIMIZE_PERMISSION = "write:*";
  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(CCSMTokenService.class);

  private final AuthCookieService authCookieService;
  private final ConfigurationService configurationService;
  private final Identity identity;

  public CCSMTokenService(
      final AuthCookieService authCookieService,
      final ConfigurationService configurationService,
      final Identity identity) {
    this.authCookieService = authCookieService;
    this.configurationService = configurationService;
    this.identity = identity;
  }

  public List<Cookie> createOptimizeAuthNewCookies(
      final Tokens tokens, final AccessToken accessToken, final String scheme) {
    LOG.trace("Creating Optimize service authorization cookie(s).");
    final String tokenValue = accessToken.getToken().getToken();
    final List<Cookie> cookies =
        new ArrayList<>(
            authCookieService.createOptimizeAuthCookies(
                tokenValue, accessToken.getToken().getExpiresAtAsInstant(), scheme));
    final Date refreshTokenExpirationDate = getRefreshTokenExpirationDate(tokens.getRefreshToken());
    final Cookie optimizeRefreshCookie =
        authCookieService.createCookie(
            OPTIMIZE_REFRESH_TOKEN,
            tokens.getRefreshToken(),
            Optional.ofNullable(refreshTokenExpirationDate).map(Date::toInstant).orElse(null),
            scheme);
    cookies.add(optimizeRefreshCookie);
    return cookies;
  }

  public List<Cookie> createOptimizeDeleteAuthCookies() {
    final List<Cookie> cookies = authCookieService.createDeleteOptimizeAuthCookies();
    cookies.add(authCookieService.createDeleteOptimizeRefreshCookie());
    return cookies;
  }

  public List<Cookie> createOptimizeDeleteAuthNewCookies() {
    final List<Cookie> cookies = authCookieService.createDeleteOptimizeAuthNewCookie(true);
    cookies.add(authCookieService.createDeleteOptimizeRefreshNewCookie(true));
    return cookies;
  }

  public URI buildAuthorizeUri(final String redirectUri) {
    // If a redirect root URL is explicitly set, we use that. Otherwise, we use the one provided
    final String authorizeUri =
        appendCallbackSubpath(getConfiguredRedirectUri().orElse(redirectUri));
    LOG.trace("Authorizing with authorizeUri: {}", authorizeUri);
    return authentication().authorizeUriBuilder(authorizeUri).build();
  }

  public Tokens exchangeAuthCode(final AuthCodeDto authCode, final URI uri) {
    // If a redirect root URL is explicitly set, we append the callback subpath and use that.
    // Otherwise, we use the one provided in the request
    final String redirectUri =
        getConfiguredRedirectUri()
            .map(CCSMTokenService::appendCallbackSubpath)
            .orElse(uri.toString());
    LOG.trace("Exchanging auth code with redirectUri: {}", redirectUri);
    try {
      return authentication().exchangeAuthCode(authCode, redirectUri);
    } catch (final CodeExchangeException | RestException e) {
      throw new NotAuthorizedException("Token exchange failed", e);
    }
  }

  private Date getRefreshTokenExpirationDate(final String refreshToken) {
    try {
      final DecodedJWT decodedRefreshToken = authentication().decodeJWT(refreshToken);
      final Date refreshTokenExpiresAt = decodedRefreshToken.getExpiresAt();
      LOG.trace("Refresh token will expire at {}", refreshTokenExpiresAt);
      return refreshTokenExpiresAt;
    } catch (final TokenDecodeException e) {
      LOG.trace(
          "Refresh token is not a JWT and expiry date can not be determined. Error message: {}",
          e.getMessage());
      return null;
    }
  }

  private static String appendCallbackSubpath(final String configuredRedirectUri) {
    return configuredRedirectUri + REST_API_PATH + AUTHENTICATION_PATH + CALLBACK;
  }

  private Optional<String> getConfiguredRedirectUri() {
    final String configuredRedirectRootUrl =
        configurationService.getAuthConfiguration().getCcsmAuthConfiguration().getRedirectRootUrl();
    return StringUtils.isEmpty(configuredRedirectRootUrl)
        ? Optional.empty()
        : Optional.of(configuredRedirectRootUrl);
  }

  public AccessToken verifyToken(final String accessToken) {
    try {
      final AccessToken verifiedToken =
          authentication().verifyToken(extractTokenFromAuthorizationValue(accessToken));
      if (!userHasOptimizeAuthorization(verifiedToken)) {
        throw new NotAuthorizedException("User is not authorized to access Optimize");
      }
      return verifiedToken;
    } catch (final TokenVerificationException ex) {
      throw new NotAuthorizedException("Token could not be verified", ex);
    }
  }

  public Tokens renewToken(final String refreshToken) {
    try {
      return authentication().renewToken(extractTokenFromAuthorizationValue(refreshToken));
    } catch (final IdentityException ex) {
      throw new NotAuthorizedException("Token could not be renewed", ex);
    }
  }

  public void revokeToken(final String refreshToken) {
    try {
      authentication().revokeToken(extractTokenFromAuthorizationValue(refreshToken));
    } catch (final IdentityException ex) {
      throw new NotAuthorizedException("Token could not be revoked", ex);
    }
  }

  public String getSubjectFromToken(final String accessToken) {
    try {
      return authentication()
          .decodeJWT(extractTokenFromAuthorizationValue(accessToken))
          .getSubject();
    } catch (final TokenDecodeException ex) {
      throw new NotAuthorizedException("Token could not be decoded", ex);
    }
  }

  public UserDto getUserInfoFromToken(final String userId, final String accessToken) {
    final UserDetails userDetails =
        verifyToken(extractTokenFromAuthorizationValue(accessToken)).getUserDetails();
    return new UserDto(
        userId,
        userDetails.getName().orElseGet(() -> userDetails.getUsername().orElse(userId)),
        userDetails.getEmail().orElse(userId),
        Collections.emptyList());
  }

  public Optional<String> getCurrentUserIdFromAuthToken() {
    try {
      // The userID is the subject of the current JWT token
      return getCurrentUserAuthToken().map(token -> authentication().decodeJWT(token).getSubject());
    } catch (final TokenDecodeException ex) {
      throw new NotAuthorizedException("Token could not be decoded", ex);
    }
  }

  public Optional<String> getCurrentUserAuthToken() {
    return Optional.ofNullable(RequestContextHolder.getRequestAttributes())
        .filter(ServletRequestAttributes.class::isInstance)
        .map(ServletRequestAttributes.class::cast)
        .map(ServletRequestAttributes::getRequest)
        .flatMap(AuthCookieService::getAuthCookieToken);
  }

  public List<TenantDto> getAuthorizedTenantsFromToken(final String accessToken) {
    try {
      return identity.tenants().forToken(accessToken).stream()
          .map(tenant -> new TenantDto(tenant.getTenantId(), tenant.getName(), ZEEBE_DATA_SOURCE))
          .toList();
    } catch (final Exception e) {
      LOG.error("Could not retrieve authorized tenants from identity.", e);
      return Collections.emptyList();
    }
  }

  private String extractTokenFromAuthorizationValue(final String cookieValue) {
    if (cookieValue.startsWith(AUTH_COOKIE_TOKEN_VALUE_PREFIX)) {
      return cookieValue.substring(AUTH_COOKIE_TOKEN_VALUE_PREFIX.length()).trim();
    }
    return cookieValue;
  }

  private Authentication authentication() {
    return identity.authentication();
  }

  private static boolean userHasOptimizeAuthorization(final AccessToken accessToken) {
    return accessToken.getPermissions().contains(OPTIMIZE_PERMISSION);
  }
}
