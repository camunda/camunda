/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.ccsm;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Optional diagnostics filter for Optimize CCSM OIDC redirect flows. Enabled via {@code
 * security.auth.ccsm.diagnosticsEnabled=true} (env: {@code
 * CAMUNDA_OPTIMIZE_SECURITY_AUTH_CCSM_DIAGNOSTICS_ENABLED}).
 *
 * <p>When active:
 *
 * <ul>
 *   <li>On the OIDC callback path, logs at DEBUG the raw {@code RequestURL} that Optimize will use
 *       as the {@code redirect_uri} in the token-exchange call to Identity, alongside the values
 *       from any {@code X-Forwarded-*} headers. A WARN is emitted when these differ — the most
 *       common root cause of the Entra/Identity redirect-loop problem.
 *   <li>Logs a WARN when the callback arrives with an authorization {@code code} parameter but
 *       there is no valid HTTP session (session-cookie domain/path mismatch).
 *   <li>After the filter chain, inspects the {@code Location} response header on non-callback
 *       requests. If the Location looks like an Identity authorization redirect, the {@code
 *       redirect_uri} embedded in it is extracted and compared against the forwarded-header-derived
 *       expected URI. A WARN is emitted if they differ.
 * </ul>
 */
public class OidcRedirectDiagnosticsFilter extends OncePerRequestFilter {

  private static final Logger LOG = LoggerFactory.getLogger(OidcRedirectDiagnosticsFilter.class);

  private static final String X_FORWARDED_PROTO = "X-Forwarded-Proto";
  private static final String X_FORWARDED_HOST = "X-Forwarded-Host";
  private static final String X_FORWARDED_PORT = "X-Forwarded-Port";
  private static final String X_FORWARDED_PREFIX = "X-Forwarded-Prefix";

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

    if (request.getRequestURI().endsWith(callbackPath)) {
      logCallbackDiagnostics(request);
      filterChain.doFilter(request, response);
    } else {
      filterChain.doFilter(request, response);
      logIdentityRedirectDiagnosticsIfPresent(request, response);
    }
  }

  private void logCallbackDiagnostics(final HttpServletRequest request) {
    final String rawUrl = request.getRequestURL().toString();
    final String forwardedProto = request.getHeader(X_FORWARDED_PROTO);
    final String forwardedHost = request.getHeader(X_FORWARDED_HOST);
    final String forwardedPort = request.getHeader(X_FORWARDED_PORT);
    final String forwardedPrefix = request.getHeader(X_FORWARDED_PREFIX);

    LOG.debug(
        "OIDC callback received: raw-url='{}', {}: '{}', {}: '{}', {}: '{}', {}: '{}'",
        rawUrl,
        X_FORWARDED_PROTO,
        forwardedProto,
        X_FORWARDED_HOST,
        forwardedHost,
        X_FORWARDED_PORT,
        forwardedPort,
        X_FORWARDED_PREFIX,
        forwardedPrefix);

    if (forwardedProto != null || forwardedHost != null) {
      final String expectedUrl = buildExpectedCallbackUrl(request);
      if (!rawUrl.equals(expectedUrl)) {
        LOG.warn(
            "OIDC callback URL mismatch: Optimize computed '{}' as the redirect_uri for token "
                + "exchange, but X-Forwarded-* headers indicate the external URL is '{}'. "
                + "Token exchange with Identity will likely fail. "
                + "Configure 'security.auth.ccsm.redirectRootUrl' to override the base URL, "
                + "or ensure the reverse proxy passes correct X-Forwarded-* headers and that "
                + "Spring's ForwardedHeaderFilter (or Tomcat's RemoteIpValve) is enabled.",
            rawUrl,
            expectedUrl);
      }
    }

    final boolean hasCode = request.getParameter("code") != null;
    if (hasCode && request.getSession(false) == null) {
      LOG.warn(
          "OIDC callback at '{}' received an authorization code but has no valid HTTP session. "
              + "This typically means the session cookie was not returned — check the cookie "
              + "domain, path, and SameSite/Secure settings.",
          request.getRequestURI());
    }
  }

  private void logIdentityRedirectDiagnosticsIfPresent(
      final HttpServletRequest request, final HttpServletResponse response) {
    final String location = response.getHeader("Location");
    if (location == null || location.isBlank()) {
      return;
    }
    final String redirectUri = extractQueryParam(location, "redirect_uri");
    if (redirectUri == null) {
      return;
    }
    // Only log if the Location looks like an Identity authorize endpoint
    if (!location.contains("/oauth2/authorize") && !location.contains("/authorize")) {
      return;
    }

    final String forwardedProto = request.getHeader(X_FORWARDED_PROTO);
    final String forwardedHost = request.getHeader(X_FORWARDED_HOST);
    if (forwardedProto == null && forwardedHost == null) {
      return;
    }

    final String expectedCallbackUrl = buildExpectedCallbackUrl(request);
    if (!expectedCallbackUrl.equals(redirectUri)) {
      LOG.warn(
          "Identity authorize redirect contains redirect_uri='{}', but the external callback URL "
              + "derived from X-Forwarded-* headers is '{}'. "
              + "Set 'security.auth.ccsm.redirectRootUrl' to the externally reachable base URL "
              + "(e.g. https://optimize.example.com) to fix this.",
          redirectUri,
          expectedCallbackUrl);
    } else {
      LOG.debug(
          "Identity authorize redirect: redirect_uri='{}' matches expected external callback URL.",
          redirectUri);
    }
  }

  private String buildExpectedCallbackUrl(final HttpServletRequest request) {
    final String proto = firstHeaderOrFallback(request, X_FORWARDED_PROTO, request.getScheme());
    // X-Forwarded-Host may embed a port (e.g. "example.com:8443"); split it out
    final String rawHost =
        firstHeaderOrFallback(request, X_FORWARDED_HOST, request.getServerName());
    final String[] hostParts = splitHostPort(rawHost);
    final String host = hostParts[0];

    // X-Forwarded-Port, if present, overrides any port embedded in X-Forwarded-Host.
    // Take the first value — proxies sometimes send comma-separated lists.
    final String portRaw = request.getHeader(X_FORWARDED_PORT);
    final String effectivePort;
    if (portRaw != null && !portRaw.isBlank()) {
      final int comma = portRaw.indexOf(',');
      effectivePort = (comma >= 0 ? portRaw.substring(0, comma) : portRaw).trim();
    } else {
      effectivePort = hostParts[1]; // port embedded in X-Forwarded-Host, or null
    }

    final String prefix = request.getHeader(X_FORWARDED_PREFIX);

    final StringBuilder sb = new StringBuilder(proto).append("://").append(host);
    if (effectivePort != null) {
      final boolean isDefaultPort =
          ("https".equals(proto) && "443".equals(effectivePort))
              || ("http".equals(proto) && "80".equals(effectivePort));
      if (!isDefaultPort) {
        sb.append(':').append(effectivePort);
      }
    }
    if (prefix != null && !prefix.isBlank()) {
      sb.append(prefix);
    }
    sb.append(request.getContextPath()).append(callbackPath);
    return sb.toString();
  }

  /**
   * Splits {@code "host"} or {@code "host:port"} into a two-element array {@code [host, port]}. The
   * port element is {@code null} when no numeric port suffix is present. Works for bare hostnames,
   * IPv4 addresses, and bracketed IPv6 addresses (e.g. {@code "[::1]:8080"}).
   */
  static String[] splitHostPort(final String hostValue) {
    final int colon = hostValue.lastIndexOf(':');
    if (colon < 0) {
      return new String[] {hostValue, null};
    }
    final String possiblePort = hostValue.substring(colon + 1);
    if (possiblePort.isEmpty() || !possiblePort.chars().allMatch(Character::isDigit)) {
      return new String[] {hostValue, null};
    }
    return new String[] {hostValue.substring(0, colon), possiblePort};
  }

  private static String firstHeaderOrFallback(
      final HttpServletRequest request, final String header, final String fallback) {
    final String value = request.getHeader(header);
    if (value == null || value.isBlank()) {
      return fallback;
    }
    final int comma = value.indexOf(',');
    return comma >= 0 ? value.substring(0, comma).trim() : value.trim();
  }

  private static String extractQueryParam(final String url, final String paramName) {
    final int q = url.indexOf('?');
    if (q < 0) {
      return null;
    }
    for (final String pair : url.substring(q + 1).split("&")) {
      final int eq = pair.indexOf('=');
      if (eq < 0) {
        continue;
      }
      final String key = urlDecode(pair.substring(0, eq));
      if (paramName.equals(key)) {
        return urlDecode(pair.substring(eq + 1));
      }
    }
    return null;
  }

  private static String urlDecode(final String value) {
    try {
      return URLDecoder.decode(value, StandardCharsets.UTF_8);
    } catch (final IllegalArgumentException e) {
      return value;
    }
  }
}
