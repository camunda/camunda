/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.filters;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Diagnostic filter that makes OIDC redirect issues visible in the application logs instead of
 * requiring browser dev-tools. It is purely observational: it only reads the request/response and
 * never mutates the response or short-circuits the chain, so it is safe to leave enabled.
 *
 * <p>It is registered only when {@code camunda.security.authentication.oidc.diagnostics.enabled} is
 * {@code true} (see {@code WebSecurityConfig#applyOidcRedirectDiagnosticsFilter}).
 *
 * <p>Before the chain runs it logs (at DEBUG) the inputs that drive the redirect computation:
 * forwarded headers, the computed external base URL, the configured callback path, the expected
 * {@code redirect_uri}, the (secret-redacted) query parameters, and the HTTP session state.
 *
 * <p>After the chain runs it logs (at WARN) the two classic redirect-loop signatures:
 *
 * <ul>
 *   <li>an authorization request ({@code /oauth2/authorization/*}) whose generated {@code Location}
 *       carries a {@code redirect_uri} that does not match the expected one;
 *   <li>a callback that received an authorization {@code code} but has no valid HTTP session.
 * </ul>
 */
public class OidcRedirectDiagnosticsFilter extends OncePerRequestFilter {

  private static final Logger LOG = LoggerFactory.getLogger(OidcRedirectDiagnosticsFilter.class);

  private static final String AUTHORIZATION_REQUEST_PREFIX = "/oauth2/authorization/";
  private static final Set<String> REDACTED_PARAMS =
      Set.of("code", "client_secret", "id_token", "access_token", "token", "state", "credential");
  private static final String REDACTED = "***";

  private final String callbackPath;

  public OidcRedirectDiagnosticsFilter(final String callbackPath) {
    this.callbackPath = callbackPath;
  }

  @Override
  protected void doFilterInternal(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final FilterChain filterChain)
      throws ServletException, IOException {

    final String externalBaseUrl = computeExternalBaseUrl(request);
    final String expectedRedirectUri = externalBaseUrl + callbackPath;

    logRequestDiagnostics(request, externalBaseUrl, expectedRedirectUri);

    try {
      filterChain.doFilter(request, response);
    } finally {
      inspectResponse(request, response, expectedRedirectUri);
    }
  }

  private void logRequestDiagnostics(
      final HttpServletRequest request,
      final String externalBaseUrl,
      final String expectedRedirectUri) {
    if (!LOG.isDebugEnabled()) {
      return;
    }
    final HttpSession session = request.getSession(false);
    LOG.debug(
        "OIDC redirect diagnostics for {} {}: forwardedHeaders={}, externalBaseUrl={}, callbackPath={}, expectedRedirectUri={}, queryParams={}, session={}",
        request.getMethod(),
        request.getRequestURI(),
        forwardedHeaders(request),
        externalBaseUrl,
        callbackPath,
        expectedRedirectUri,
        redactedQueryParams(request),
        session == null ? "none" : "id=present,new=%s".formatted(session.isNew()));
  }

  private void inspectResponse(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final String expectedRedirectUri) {
    final String requestUri = request.getRequestURI();

    if (isAuthorizationRequest(requestUri)) {
      final String location = response.getHeader(HttpHeaders.LOCATION);
      final String actualRedirectUri = extractRedirectUri(location);
      if (actualRedirectUri != null && !actualRedirectUri.equals(expectedRedirectUri)) {
        LOG.warn(
            "OIDC redirect_uri mismatch on {}: the authorization request sends redirect_uri={} but the server expected {} (derived from the external base URL + callback path). "
                + "This is a classic cause of redirect loops; verify the IdP redirect URI registration and any reverse-proxy X-Forwarded-* headers.",
            requestUri,
            actualRedirectUri,
            expectedRedirectUri);
      }
    }

    if (isCallback(requestUri)
        && request.getParameter("code") != null
        && request.getSession(false) == null) {
      LOG.warn(
          "OIDC callback {} received an authorization code but there is no valid HTTP session. "
              + "The session created at the start of the login was lost before the callback (likely a redirect/login loop); "
              + "check session cookie attributes (SameSite/Secure/domain) and that the external base URL matches across the login and callback requests.",
          requestUri);
    }
  }

  private boolean isAuthorizationRequest(final String requestUri) {
    return requestUri != null && requestUri.contains(AUTHORIZATION_REQUEST_PREFIX);
  }

  private boolean isCallback(final String requestUri) {
    return requestUri != null && callbackPath != null && requestUri.startsWith(callbackPath);
  }

  private static String extractRedirectUri(final String location) {
    if (location == null || location.isBlank()) {
      return null;
    }
    try {
      final var params = UriComponentsBuilder.fromUriString(location).build().getQueryParams();
      final String value = params.getFirst("redirect_uri");
      return value == null ? null : URI.create(value).toString();
    } catch (final RuntimeException e) {
      LOG.debug("Could not parse redirect_uri from Location header '{}'", location, e);
      return null;
    }
  }

  private static Map<String, String> forwardedHeaders(final HttpServletRequest request) {
    final Map<String, String> headers = new LinkedHashMap<>();
    for (final String name :
        new String[] {
          "X-Forwarded-Proto",
          "X-Forwarded-Host",
          "X-Forwarded-Port",
          "X-Forwarded-Prefix",
          "Forwarded"
        }) {
      final String value = request.getHeader(name);
      if (value != null) {
        headers.put(name, value);
      }
    }
    return headers;
  }

  private static Map<String, String> redactedQueryParams(final HttpServletRequest request) {
    final Map<String, String> params = new LinkedHashMap<>();
    request
        .getParameterMap()
        .forEach(
            (key, values) ->
                params.put(
                    key, REDACTED_PARAMS.contains(key) ? REDACTED : String.join(",", values)));
    return params;
  }

  /**
   * Computes the externally-visible base URL ({@code scheme://host[:port][prefix]}), honouring
   * reverse-proxy forwarded headers when present and falling back to the servlet request otherwise.
   */
  private static String computeExternalBaseUrl(final HttpServletRequest request) {
    final UriComponentsBuilder builder =
        UriComponentsBuilder.fromUriString(request.getRequestURL().toString())
            .replacePath(null)
            .replaceQuery(null);

    final String forwardedProto = request.getHeader("X-Forwarded-Proto");
    if (forwardedProto != null && !forwardedProto.isBlank()) {
      builder.scheme(firstToken(forwardedProto));
    }

    final String forwardedHost = request.getHeader("X-Forwarded-Host");
    if (forwardedHost != null && !forwardedHost.isBlank()) {
      final String host = firstToken(forwardedHost);
      final int colon = host.indexOf(':');
      if (colon >= 0) {
        builder.host(host.substring(0, colon)).port(host.substring(colon + 1));
      } else {
        builder.host(host).port(null);
      }
    }

    final String forwardedPort = request.getHeader("X-Forwarded-Port");
    if (forwardedPort != null && !forwardedPort.isBlank()) {
      builder.port(firstToken(forwardedPort));
    }

    final String forwardedPrefix = request.getHeader("X-Forwarded-Prefix");
    final String prefix =
        forwardedPrefix != null && !forwardedPrefix.isBlank()
            ? stripTrailingSlash(firstToken(forwardedPrefix))
            : stripTrailingSlash(request.getContextPath());

    return builder.build().toUriString() + prefix;
  }

  private static String firstToken(final String headerValue) {
    final int comma = headerValue.indexOf(',');
    return (comma >= 0 ? headerValue.substring(0, comma) : headerValue).trim();
  }

  private static String stripTrailingSlash(final String value) {
    if (value == null || value.isBlank() || "/".equals(value)) {
      return "";
    }
    return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
  }
}
