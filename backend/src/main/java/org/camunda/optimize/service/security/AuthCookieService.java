/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.ext.RuntimeDelegate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.security.AuthConfiguration;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.camunda.optimize.rest.constants.RestConstants.AUTH_COOKIE_TOKEN_VALUE_PREFIX;
import static org.camunda.optimize.rest.constants.RestConstants.OPTIMIZE_AUTHORIZATION;
import static org.camunda.optimize.rest.constants.RestConstants.OPTIMIZE_REFRESH_TOKEN;
import static org.camunda.optimize.rest.constants.RestConstants.OPTIMIZE_SERVICE_TOKEN;
import static org.camunda.optimize.rest.constants.RestConstants.SAME_SITE_COOKIE_FLAG;
import static org.camunda.optimize.rest.constants.RestConstants.SAME_SITE_COOKIE_STRICT_VALUE;

@RequiredArgsConstructor
@Component
@Slf4j
public class AuthCookieService {

  private final ConfigurationService configurationService;

  public NewCookie createDeleteOptimizeAuthCookie(final String requestScheme) {
    return createDeleteOptimizeAuthNewCookie(isSecureScheme(requestScheme));
  }

  public jakarta.servlet.http.Cookie createDeleteOptimizeAuthCookie() {
    log.trace("Deleting Optimize authentication cookie.");
    return createDeleteCookie(OPTIMIZE_AUTHORIZATION, "", "https");
  }

  public NewCookie createDeleteOptimizeAuthNewCookie(final boolean secure) {
    log.trace("Deleting Optimize authentication cookie.");
    return new NewCookie.Builder(OPTIMIZE_AUTHORIZATION)
      .value("")
      .path(getCookiePath())
      .domain(null)
      .comment("delete cookie")
      .maxAge(0)
      .secure(secure)
      .httpOnly(true)
      .build();
  }

  public jakarta.servlet.http.Cookie createDeleteOptimizeRefreshCookie() {
    log.trace("Deleting Optimize refresh cookie.");
    return createDeleteCookie(OPTIMIZE_REFRESH_TOKEN, "", "https");
  }

  public NewCookie createDeleteOptimizeRefreshNewCookie(final boolean secure) {
    log.trace("Deleting Optimize refresh cookie.");
    return new NewCookie.Builder(OPTIMIZE_REFRESH_TOKEN)
      .value("")
      .path(getCookiePath())
      .domain(null)
      .comment("delete cookie")
      .maxAge(0)
      .secure(secure)
      .httpOnly(true)
      .build();
  }

  public String createNewOptimizeAuthCookie(final String optimizeAuthCookieToken, final String requestScheme) {
    return createNewOptimizeAuthCookie(
      optimizeAuthCookieToken,
      getOptimizeAuthCookieTokenExpiryDate(optimizeAuthCookieToken).orElse(null),
      requestScheme
    );
  }

  public String createNewOptimizeAuthCookie(final String optimizeAuthCookieToken,
                                            final Instant expiresAt,
                                            final String requestScheme) {
    log.trace("Creating Optimize authentication cookie.");

    return createCookie(
      OPTIMIZE_AUTHORIZATION,
      AuthCookieService.createOptimizeAuthCookieValue(optimizeAuthCookieToken),
      requestScheme,
      convertInstantToDate(expiresAt)
    );
  }

  public Optional<Instant> getOptimizeAuthCookieTokenExpiryDate(final String optimizeAuthCookieToken) {
    return getTokenIssuedAt(optimizeAuthCookieToken)
      .map(Date::toInstant)
      .map(issuedAt -> issuedAt.plus(getAuthConfiguration().getTokenLifeTimeMinutes(), ChronoUnit.MINUTES));
  }

  public jakarta.servlet.http.Cookie createDeleteCookie(final String cookieName, final String cookieValue,
                                                        final String requestScheme) {
    return createCookie(cookieName, cookieValue, Instant.now(), requestScheme, true);
  }

  public jakarta.servlet.http.Cookie createOptimizeAuthCookie(final String cookieValue, final Instant expiresAt,
                                                            final String requestScheme) {
    return createCookie(OPTIMIZE_AUTHORIZATION, cookieValue, expiresAt, requestScheme, false);
  }

  public jakarta.servlet.http.Cookie createCookie(final String cookieName, final String cookieValue,
                                                  final Instant expiresAt, final String requestScheme) {
    return createCookie(cookieName, cookieValue, expiresAt, requestScheme, false);
  }

  public List<jakarta.servlet.http.Cookie> createOptimizeServiceTokenCookies(final OAuth2AccessToken accessToken,
                                                                             final Instant expiresAt,
                                                                             final String requestScheme) {
    log.trace("Creating Optimize service token cookie(s).");
    final String tokenValue = accessToken.getTokenValue();
    final int maxCookieLength = configurationService.getAuthConfiguration().getCookieConfiguration().getMaxSize();
    final int numberOfCookies = (int) Math.ceil((double) tokenValue.length() / maxCookieLength);
    List<jakarta.servlet.http.Cookie> cookies = new ArrayList<>();
    for (int i = 0; i < numberOfCookies; i++) {
      cookies.add(createCookie(
        getServiceCookieNameWithSuffix(i),
        /* creates a substring of the cookie token value based on the index 'i' and the maximum cookie length
          'maxCookieLength'. If the current index 'i' is equal to the number of cookies minus one, it takes the
          remaining characters of the token value from the current index multiplied by the maximum cookie length
          until the end of the string, otherwise it takes a substring starting from the current index multiplied by
          the maximum cookie length with a length of the maximum cookie length.
         */
        i == (numberOfCookies - 1) ? tokenValue.substring((i * maxCookieLength)) :
          tokenValue.substring((i * maxCookieLength), ((i * maxCookieLength) + maxCookieLength)),
        expiresAt,
        requestScheme
      ));
    }
    return cookies;
  }

  private jakarta.servlet.http.Cookie createCookie(final String cookieName, final String cookieValue,
                                                   final Instant expiresAt, final String requestScheme, final boolean isDelete) {
    String cookiePath = getCookiePath();
    if (getAuthConfiguration().getCookieConfiguration().isSameSiteFlagEnabled()) {
      cookiePath = addSameSiteCookieFlag(cookiePath);
    }
    final jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie(cookieName, cookieValue);
    cookie.setPath(cookiePath);
    cookie.setHttpOnly(true);
    cookie.setSecure(isSecureScheme(requestScheme));

    if (expiresAt == null) {
      cookie.setMaxAge(-1);
    } else {
      cookie.setMaxAge(isDelete ? 0 : (int) Duration.between(Instant.now(), expiresAt).toSeconds());
    }

    return cookie;
  }

  public NewCookie createCookie(final String cookieName, final String cookieValue,
                                final Date expiresAt, final String requestScheme) {
    String cookiePath = getCookiePath();
    if (getAuthConfiguration().getCookieConfiguration().isSameSiteFlagEnabled()) {
      cookiePath = addSameSiteCookieFlag(cookiePath);
    }
    return new NewCookie.Builder(cookieName)
      .value(cookieValue)
      .path(cookiePath)
      .domain(null)
      .version(1)
      .comment(null)
      .maxAge(-1)
      .expiry(expiresAt)
      .secure(isSecureScheme(requestScheme))
      .httpOnly(true)
      .build();
  }

  public static Optional<String> getAuthCookieToken(HttpServletRequest servletRequest) {
    return Optional.ofNullable((String) servletRequest.getAttribute(OPTIMIZE_AUTHORIZATION))
      .or(() -> extractAuthorizationValueFromCookies(servletRequest))
      .or(() -> extractAuthorizationValueFromCookieHeader(servletRequest))
      .flatMap(AuthCookieService::extractTokenFromAuthorizationValue);
  }

  private static Optional<String> extractAuthorizationValueFromCookies(final HttpServletRequest servletRequest) {
    final jakarta.servlet.http.Cookie[] cookies = servletRequest.getCookies();
    if (cookies != null) {
      for (jakarta.servlet.http.Cookie cookie : cookies) {
        if (OPTIMIZE_AUTHORIZATION.equals(cookie.getName())) {
          return Optional.of(cookie.getValue());
        }
      }
    }
    return Optional.empty();
  }

  private static Optional<String> extractAuthorizationValueFromCookieHeader(final HttpServletRequest servletRequest) {
    String cookieHeader = servletRequest.getHeader("Cookie");
    if (cookieHeader != null) {
      // In the header we have a series of values of the type a=b;c=d;d=e
      String[] cookiePairs = cookieHeader.split(";");
      for (String cookiePair : cookiePairs) {
        // We are looking for "foo" in something like X-Optimize-Authorization=foo
        Pattern pattern = Pattern.compile("\\s*" + OPTIMIZE_AUTHORIZATION + "\\s*=\\s*(.*)");
        Matcher matcher = pattern.matcher(cookiePair);
        if (matcher.find()) {
          // We found it, so now we extract the value
          String value = matcher.group(1);
          // Trim white spaces and tabs to get only the value
          return Optional.of(value.replace("\"", "").trim());
        }
      }
    }
    return Optional.empty();
  }

  public static Optional<String> getAuthCookieToken(ContainerRequestContext requestContext) {
    // load just issued token if set by previous filter
    String authorizationValue = (String) requestContext.getProperty(OPTIMIZE_AUTHORIZATION);
    if (authorizationValue == null) {
      for (Map.Entry<String, Cookie> cookieEntry : requestContext.getCookies().entrySet()) {
        if (OPTIMIZE_AUTHORIZATION.equals(cookieEntry.getKey())) {
          authorizationValue = cookieEntry.getValue().getValue();
        }
      }
    }
    return Optional.ofNullable(authorizationValue).flatMap(AuthCookieService::extractTokenFromAuthorizationValue);
  }

  public static Optional<String> getServiceAccessToken(HttpServletRequest servletRequest) {
    boolean serviceTokenExtracted = false;
    int serviceTokenSuffixToExtract = 0;
    StringBuilder serviceAccessToken = new StringBuilder();
    while (!serviceTokenExtracted) {
      final String serviceCookieName = getServiceCookieNameWithSuffix(serviceTokenSuffixToExtract);
      String authorizationValue = null;
      if (servletRequest.getCookies() != null) {
        for (jakarta.servlet.http.Cookie cookie : servletRequest.getCookies()) {
          if (serviceCookieName.equals(cookie.getName())) {
            authorizationValue = cookie.getValue();
          }
        }
      }
      if (authorizationValue != null) {
        serviceAccessToken.append(authorizationValue);
        serviceTokenSuffixToExtract += 1;
      } else {
        serviceTokenExtracted = true;
      }
    }
    if (serviceAccessToken.length() != 0) {
      return Optional.of(serviceAccessToken.toString().trim());
    }
    return Optional.empty();
  }

  public static Optional<String> getTokenSubject(String token) {
    return getTokenAttribute(token, DecodedJWT::getSubject);
  }

  public static String createOptimizeAuthCookieValue(final String tokenValue) {
    return AUTH_COOKIE_TOKEN_VALUE_PREFIX + tokenValue;
  }

  private Date convertInstantToDate(final Instant expiresAt) {
    return Optional.ofNullable(expiresAt).map(Date::from).orElse(null);
  }

  private String createCookie(final String cookieName, final String cookieValue, final String requestScheme,
                              final Date expiryDate) {
    NewCookie newCookie = new NewCookie.Builder(cookieName)
      .value(cookieValue)
      .path(getCookiePath())
      .domain(null)
      .version(1)
      .comment(null)
      .maxAge(-1)
      .expiry(expiryDate)
      .secure(isSecureScheme(requestScheme))
      .httpOnly(true)
      .build();

    String newCookieAsString = RuntimeDelegate.getInstance().createHeaderDelegate(NewCookie.class).toString(newCookie);
    if (getAuthConfiguration().getCookieConfiguration().isSameSiteFlagEnabled()) {
      newCookieAsString = addSameSiteCookieFlag(newCookieAsString);
    }
    return newCookieAsString;
  }

  private String getCookiePath() {
    return "/" + configurationService.getAuthConfiguration().getCloudAuthConfiguration().getClusterId();
  }

  private boolean isSecureScheme(final String requestScheme) {
    return configurationService.getAuthConfiguration().getCookieConfiguration().resolveSecureFlagValue(requestScheme);
  }

  private AuthConfiguration getAuthConfiguration() {
    return configurationService.getAuthConfiguration();
  }

  private String addSameSiteCookieFlag(String newCookieAsString) {
    return newCookieAsString + String.format(";%s=%s", SAME_SITE_COOKIE_FLAG, SAME_SITE_COOKIE_STRICT_VALUE);
  }

  private static Optional<Date> getTokenIssuedAt(String token) {
    return getTokenAttribute(token, DecodedJWT::getIssuedAt);
  }

  private static <T> Optional<T> getTokenAttribute(final String token,
                                                   final Function<DecodedJWT, T> getTokenAttributeFunction) {
    try {
      final DecodedJWT decoded = JWT.decode(token);
      return Optional.of(getTokenAttributeFunction.apply(decoded));
    } catch (Exception e) {
      log.debug("Could not decode security token to extract attribute!", e);
    }
    return Optional.empty();
  }

  private static String getServiceCookieNameWithSuffix(final int suffix) {
    return OPTIMIZE_SERVICE_TOKEN + "_" + suffix;
  }

  private static Optional<String> extractTokenFromAuthorizationValue(final String authCookieValue) {
    if (authCookieValue != null && authCookieValue.startsWith(AUTH_COOKIE_TOKEN_VALUE_PREFIX)) {
      return Optional.of(authCookieValue.substring(AUTH_COOKIE_TOKEN_VALUE_PREFIX.length()).trim());
    }
    return Optional.ofNullable(authCookieValue);
  }

}
