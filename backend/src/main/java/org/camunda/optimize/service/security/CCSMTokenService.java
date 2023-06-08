/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.security;

import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.IdentityConfiguration;
import io.camunda.identity.sdk.authentication.AccessToken;
import io.camunda.identity.sdk.authentication.Authentication;
import io.camunda.identity.sdk.authentication.Tokens;
import io.camunda.identity.sdk.authentication.UserDetails;
import io.camunda.identity.sdk.authentication.dto.AuthCodeDto;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.CCSMCondition;
import org.camunda.optimize.service.util.configuration.security.CCSMAuthConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import javax.servlet.http.Cookie;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.core.NewCookie;
import java.net.URI;
import java.util.Collections;
import java.util.List;

import static org.camunda.optimize.jetty.OptimizeResourceConstants.REST_API_PATH;
import static org.camunda.optimize.rest.AuthenticationRestService.AUTHENTICATION_PATH;
import static org.camunda.optimize.rest.AuthenticationRestService.CALLBACK;
import static org.camunda.optimize.rest.constants.RestConstants.AUTH_COOKIE_TOKEN_VALUE_PREFIX;
import static org.camunda.optimize.rest.constants.RestConstants.OPTIMIZE_AUTHORIZATION;
import static org.camunda.optimize.rest.constants.RestConstants.OPTIMIZE_REFRESH_TOKEN;

@AllArgsConstructor
@Component
@Slf4j
@Conditional(CCSMCondition.class)
public class CCSMTokenService {

  // In Identity, Optimize requires users to have write access to everything
  private static final String OPTIMIZE_PERMISSION = "write:*";

  private final AuthCookieService authCookieService;
  private final ConfigurationService configurationService;

  @Bean
  private Identity identity() {
    return new Identity(identityConfiguration());
  }

  public List<Cookie> createOptimizeAuthCookies(final Tokens tokens, final AccessToken accessToken, final String scheme) {
    final Cookie optimizeAuthCookie = authCookieService.createCookie(
      OPTIMIZE_AUTHORIZATION,
      accessToken.getToken().getToken(),
      accessToken.getToken().getExpiresAtAsInstant(),
      scheme
    );
    final Cookie optimizeRefreshCookie = authCookieService.createCookie(
      OPTIMIZE_REFRESH_TOKEN,
      tokens.getRefreshToken(),
      authentication().decodeJWT(tokens.getRefreshToken()).getExpiresAt().toInstant(),
      scheme
    );
    return List.of(optimizeAuthCookie, optimizeRefreshCookie);
  }

  public List<NewCookie> createOptimizeAuthNewCookies(final Tokens tokens, final AccessToken accessToken, final String scheme) {
    final NewCookie optimizeAuthCookie = authCookieService.createCookie(
      OPTIMIZE_AUTHORIZATION,
      accessToken.getToken().getToken(),
      accessToken.getToken().getExpiresAt(),
      scheme
    );
    final NewCookie optimizeRefreshCookie = authCookieService.createCookie(
      OPTIMIZE_REFRESH_TOKEN,
      tokens.getRefreshToken(),
      authentication().decodeJWT(tokens.getRefreshToken()).getExpiresAt(),
      scheme
    );
    return List.of(optimizeAuthCookie, optimizeRefreshCookie);
  }

  public List<Cookie> createOptimizeDeleteAuthCookies() {
    return List.of(
      authCookieService.createDeleteOptimizeAuthCookie(),
      authCookieService.createDeleteOptimizeRefreshCookie()
    );
  }

  public List<NewCookie> createOptimizeDeleteAuthNewCookies() {
    return List.of(
      authCookieService.createDeleteOptimizeAuthNewCookie(true),
      authCookieService.createDeleteOptimizeRefreshNewCookie(true)
    );
  }

  public Tokens exchangeAuthCode(final AuthCodeDto authCode, final String redirectUri) {
    return authentication().exchangeAuthCode(authCode, redirectUri);
  }

  public URI buildAuthorizeUri(final String redirectUri) {
    // If a redirect root URL is explicitly set, we use that. Otherwise, we use the one provided
    final String redirectRootUrl = configurationService.getAuthConfiguration().getCcsmAuthConfiguration().getRedirectRootUrl();
    return authentication().authorizeUriBuilder(
        StringUtils.isEmpty(redirectRootUrl) ? redirectUri : redirectRootUrl + REST_API_PATH + AUTHENTICATION_PATH + CALLBACK)
      .build();
  }

  public AccessToken verifyToken(final String accessToken) {
    final AccessToken verifiedToken = authentication().verifyToken(extractTokenFromAuthorizationValue(accessToken));
    if (!userHasOptimizeAuthorization(verifiedToken)) {
      throw new NotAuthorizedException("User is not authorized to access Optimize");
    }
    return verifiedToken;
  }

  public Tokens renewToken(final String refreshToken) {
    return authentication().renewToken(extractTokenFromAuthorizationValue(refreshToken));
  }

  public void revokeToken(final String refreshToken) {
    authentication().revokeToken(extractTokenFromAuthorizationValue(refreshToken));
  }

  public String getSubjectFromToken(final String accessToken) {
    return authentication().decodeJWT(extractTokenFromAuthorizationValue(accessToken)).getSubject();
  }

  public UserDto getUserInfoFromToken(final String userId, final String accessToken) {
    final UserDetails userDetails = verifyToken(extractTokenFromAuthorizationValue(accessToken)).getUserDetails();
    return new UserDto(
      userId,
      userDetails.getName().orElse(userId),
      userDetails.getEmail().orElse(userId),
      Collections.emptyList()
    );
  }

  private String extractTokenFromAuthorizationValue(final String cookieValue) {
    if (cookieValue.startsWith(AUTH_COOKIE_TOKEN_VALUE_PREFIX)) {
      return cookieValue.substring(AUTH_COOKIE_TOKEN_VALUE_PREFIX.length()).trim();
    }
    return cookieValue;
  }

  private Authentication authentication() {
    return identity().authentication();
  }

  private IdentityConfiguration identityConfiguration() {
    final CCSMAuthConfiguration ccsmAuthConfig = configurationService.getAuthConfiguration().getCcsmAuthConfiguration();
    return new IdentityConfiguration(
      ccsmAuthConfig.getIssuerUrl(), ccsmAuthConfig.getIssuerBackendUrl(),
      ccsmAuthConfig.getClientId(), ccsmAuthConfig.getClientSecret(), ccsmAuthConfig.getAudience()
    );
  }

  private static boolean userHasOptimizeAuthorization(final AccessToken accessToken) {
    return accessToken.getPermissions().contains(OPTIMIZE_PERMISSION);
  }

}
