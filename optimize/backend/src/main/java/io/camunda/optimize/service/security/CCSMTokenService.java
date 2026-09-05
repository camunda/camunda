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
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.api.model.CamundaAuthentication;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Conditional;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
@Conditional(CCSMCondition.class)
public class CCSMTokenService {

  // In Identity, Optimize requires users to have write access to everything
  private static final String OPTIMIZE_PERMISSION = "write:*";
  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(CCSMTokenService.class);

  // Known Microsoft Entra / Azure AD issuer host roots across all cloud deployments:
  //   Commercial:    login.microsoftonline.com, sts.windows.net
  //   US Government: login.microsoftonline.us
  //   Germany:       login.microsoftonline.de  (legacy sovereign, still active)
  //   China:         login.partner.microsoftonline.cn, sts.chinacloudapi.cn
  // Each entry matches both the exact host and any subdomain (e.g.
  // "tenant.login.microsoftonline.us").
  private static final List<String> ENTRA_ISSUER_ROOT_DOMAINS =
      List.of(
          "login.microsoftonline.com",
          "login.microsoftonline.us",
          "login.microsoftonline.de",
          "login.partner.microsoftonline.cn",
          "sts.windows.net",
          "sts.chinacloudapi.cn");

  private final AuthCookieService authCookieService;
  private final ConfigurationService configurationService;
  private final Identity identity;
  // Both present only under CSL; absent in the legacy CCSM setup, which falls back to the auth
  // cookie and the token's sub claim.
  private final ObjectProvider<OAuth2AuthorizedClientRepository> authorizedClientRepositoryProvider;
  private final ObjectProvider<CamundaAuthenticationProvider> camundaAuthenticationProviderProvider;

  public CCSMTokenService(
      final AuthCookieService authCookieService,
      final ConfigurationService configurationService,
      final Identity identity,
      final ObjectProvider<OAuth2AuthorizedClientRepository> authorizedClientRepositoryProvider,
      final ObjectProvider<CamundaAuthenticationProvider> camundaAuthenticationProviderProvider) {
    this.authCookieService = authCookieService;
    this.configurationService = configurationService;
    this.identity = identity;
    this.authorizedClientRepositoryProvider = authorizedClientRepositoryProvider;
    this.camundaAuthenticationProviderProvider = camundaAuthenticationProviderProvider;
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

  /**
   * Verifies the validity and Optimize authorization of the access token.
   *
   * <p>This method should be used ONLY as the first step in token validation, before attempting
   * token renewal. If verification fails with {@link TokenVerificationException}, the caller should
   * proceed to attempt token renewal using {@link #renewToken(String)}.
   *
   * @param accessToken the access token to verify (may include "Bearer" prefix)
   * @throws TokenVerificationException if the token is invalid or expired
   * @throws NotAuthorizedException if the token is valid but the user lacks Optimize permissions
   */
  public void verifyAccessToken(final String accessToken) {
    final AccessToken verifiedToken =
        authentication().verifyToken(extractTokenFromAuthorizationValue(accessToken));
    if (!userHasOptimizeAuthorization(verifiedToken)) {
      throw new NotAuthorizedException("User is not authorized to access Optimize");
    }
    validateEntraTokenVersion(accessToken);
  }

  public AccessToken verifyToken(final String accessToken) {
    try {
      final AccessToken verifiedToken =
          authentication().verifyToken(extractTokenFromAuthorizationValue(accessToken));
      if (!userHasOptimizeAuthorization(verifiedToken)) {
        throw new NotAuthorizedException("User is not authorized to access Optimize");
      }
      validateEntraTokenVersion(accessToken);
      return verifiedToken;
    } catch (final TokenVerificationException ex) {
      throw new NotAuthorizedException("Token could not be verified", ex);
    }
  }

  /**
   * Rejects Microsoft Entra (Azure AD) access tokens that were issued in v1.0 format ({@code ver !=
   * "2.0"}). Entra issues v1.0 tokens by default when {@code api.requestedAccessTokenVersion} is
   * not set on the app registration. Camunda requires v2.0 because v1.0 tokens use a different
   * audience format that causes silent redirect loops instead of clear auth errors.
   *
   * <p>When a v1.0 token is detected, this method logs an actionable ERROR message and throws
   * {@link NotAuthorizedException} so the failure surfaces immediately.
   *
   * <p>Gated by {@code security.auth.ccsm.entraTokenVersionCheckEnabled} (default {@code true}).
   * Set to {@code false} as a last-resort escape hatch if a deployment cannot immediately update
   * the Azure app registration.
   */
  private void validateEntraTokenVersion(final String rawToken) {
    if (!configurationService
        .getAuthConfiguration()
        .getCcsmAuthConfiguration()
        .isEntraTokenVersionCheckEnabled()) {
      return;
    }
    final DecodedJWT decoded;
    try {
      decoded = authentication().decodeJWT(extractTokenFromAuthorizationValue(rawToken));
    } catch (final TokenDecodeException e) {
      return;
    }
    final String issuer = decoded.getIssuer();
    if (issuer == null || !isMicrosoftEntraIssuer(issuer)) {
      return;
    }
    final String ver = decoded.getClaim("ver").asString();
    if (!"2.0".equals(ver)) {
      final String msg =
          "Microsoft Entra token rejected: 'ver' claim is '"
              + ver
              + "' but '2.0' is required. "
              + "Set 'api.requestedAccessTokenVersion = 2' on the Camunda app registration "
              + "in the Azure portal to enable v2.0 access tokens.";
      LOG.error(msg);
      throw new NotAuthorizedException(msg);
    }
  }

  static boolean isMicrosoftEntraIssuer(final String issuer) {
    final String host;
    try {
      host = URI.create(issuer).getHost();
    } catch (final IllegalArgumentException e) {
      return false;
    }
    if (host == null) {
      return false;
    }
    final String lower = host.toLowerCase(Locale.ROOT);
    return ENTRA_ISSUER_ROOT_DOMAINS.stream()
        .anyMatch(domain -> lower.equals(domain) || lower.endsWith("." + domain));
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
    // Under CSL the principal is resolved from the configured username-claim, which may not be the
    // sub claim. Prefer it over re-decoding the access token (whose subject is always sub), so the
    // resolved user id matches the CSL SecurityContext principal.
    final Optional<String> cslUserId = cslAuthenticatedUsername();
    if (cslUserId.isPresent()) {
      return cslUserId;
    }
    try {
      // The userID is the subject of the current JWT token
      return getCurrentUserAuthToken().map(token -> authentication().decodeJWT(token).getSubject());
    } catch (final TokenDecodeException ex) {
      throw new NotAuthorizedException("Token could not be decoded", ex);
    }
  }

  private Optional<String> cslAuthenticatedUsername() {
    final CamundaAuthenticationProvider provider =
        camundaAuthenticationProviderProvider == null
            ? null
            : camundaAuthenticationProviderProvider.getIfAvailable();
    if (provider == null) {
      return Optional.empty();
    }
    if (!CslAuthentication.isCslAuthenticatedRequest()) {
      return Optional.empty();
    }
    return Optional.ofNullable(provider.getCamundaAuthentication())
        .map(CamundaAuthentication::authenticatedUsername);
  }

  public Optional<String> getCurrentUserAuthToken() {
    final HttpServletRequest request = currentRequest().orElse(null);
    if (request == null) {
      return Optional.empty();
    }
    // Under CSL the token lives in the OIDC session's authorized client; fall back to the auth
    // cookie for the legacy CCSM setup, then to the validated bearer token for stateless API
    // callers (e.g. the Web Modeler cluster proxy) that carry neither a session nor an auth cookie.
    return cslSessionAccessToken(request)
        .or(() -> AuthCookieService.getAuthCookieToken(request))
        .or(this::cslBearerAccessToken);
  }

  // Only under CSL: the legacy public API path (api.jwtAuthForApiEnabled) also authenticates as a
  // JwtAuthenticationToken, which must keep resolving no token here to avoid per-tenant Identity
  // calls for API requests that previously resolved none.
  private Optional<String> cslBearerAccessToken() {
    if (!isCslActive()) {
      return Optional.empty();
    }
    if (SecurityContextHolder.getContext().getAuthentication()
        instanceof final JwtAuthenticationToken jwtToken) {
      return Optional.ofNullable(jwtToken.getToken())
          .map(Jwt::getTokenValue)
          .filter(StringUtils::isNotBlank);
    }
    return Optional.empty();
  }

  private boolean isCslActive() {
    return camundaAuthenticationProviderProvider != null
        && camundaAuthenticationProviderProvider.getIfAvailable() != null;
  }

  private Optional<HttpServletRequest> currentRequest() {
    return Optional.ofNullable(RequestContextHolder.getRequestAttributes())
        .filter(ServletRequestAttributes.class::isInstance)
        .map(ServletRequestAttributes.class::cast)
        .map(ServletRequestAttributes::getRequest);
  }

  private Optional<String> cslSessionAccessToken(final HttpServletRequest request) {
    final OAuth2AuthorizedClientRepository repository =
        authorizedClientRepositoryProvider == null
            ? null
            : authorizedClientRepositoryProvider.getIfAvailable();
    if (repository == null) {
      return Optional.empty();
    }
    if (!(SecurityContextHolder.getContext().getAuthentication()
        instanceof final OAuth2AuthenticationToken oauthToken)) {
      return Optional.empty();
    }
    final OAuth2AuthorizedClient client =
        repository.loadAuthorizedClient(
            oauthToken.getAuthorizedClientRegistrationId(), oauthToken, request);
    return Optional.ofNullable(client)
        .map(OAuth2AuthorizedClient::getAccessToken)
        .map(token -> token.getTokenValue());
  }

  // Propagates Identity failures so callers can distinguish a failed lookup (worth retrying) from a
  // user who genuinely has no tenants (a valid, cacheable result).
  public List<TenantDto> getAuthorizedTenantsFromToken(final String accessToken) {
    return identity.tenants().forToken(accessToken).stream()
        .map(tenant -> new TenantDto(tenant.getTenantId(), tenant.getName(), ZEEBE_DATA_SOURCE))
        .toList();
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
