/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.active;

import io.camunda.authentication.config.spi.SecurityPaths;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.util.HtmlUtils;

/**
 * Shared pieces of the blocked-login experience: request classification, the marker cookie and the
 * HTML page. {@link InactiveUserFilter} sets the marker cookie when it ends the session of a
 * blocked user; {@link BlockedLoginPageFilter} shows the page for the following unauthenticated
 * browser navigation, which would otherwise be redirected to the identity provider.
 */
final class BlockedLogin {

  static final String COOKIE_NAME = "CAMUNDA_LOGIN_BLOCKED";
  static final String RESET_PATH = "/blocked-login/reset";
  static final int COOKIE_MAX_AGE_SECONDS = 600;

  private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
  private static final String TENANT_PREFIX_PATTERN = "/physical-tenants/*";
  private static final String PAGE_TEMPLATE = loadTemplate();
  private static final Set<String> STATIC_EXTENSIONS =
      Set.of(
          "js", "mjs", "css", "map", "png", "svg", "ico", "jpg", "jpeg", "gif", "webp", "woff",
          "woff2", "ttf");
  private static final String ASSETS_PATTERN = "/**/assets/**";

  private static final String DEACTIVATED_TITLE = "Your account is deactivated";
  private static final String DEACTIVATED_TEXT =
      "Your identity provider has deactivated your account, so you can't use Camunda"
          + " right now. If you think this is a mistake, contact your administrator.";
  private static final String MISMATCH_TITLE = "This sign-in can't be used";
  private static final String MISMATCH_TEXT =
      "You signed in through a different identity provider than the one that manages"
          + " your Camunda account. Sign in again with the right provider, or contact"
          + " your administrator.";

  private BlockedLogin() {}

  enum Variant {
    DEACTIVATED(DEACTIVATED_TITLE, DEACTIVATED_TEXT),
    MISMATCH(MISMATCH_TITLE, MISMATCH_TEXT);

    private final String title;
    private final String text;

    Variant(final String title, final String text) {
      this.title = title;
      this.text = text;
    }
  }

  /** What the blocked-login page shows. Null source fields are omitted from the page. */
  record Marker(Variant variant, String username, String loginSource, String managedBySource) {

    String encode() {
      final var encoder = Base64.getUrlEncoder().withoutPadding();
      return String.join(
          ".",
          part(encoder, variant.name()),
          part(encoder, username),
          part(encoder, loginSource),
          part(encoder, managedBySource));
    }

    static Marker decode(final String value) {
      if (value == null) {
        return null;
      }
      try {
        final var parts = value.split("\\.", -1);
        if (parts.length != 4) {
          return null;
        }
        final var decoder = Base64.getUrlDecoder();
        final var variant = Variant.valueOf(unpart(decoder, parts[0]));
        final var username = unpart(decoder, parts[1]);
        return username == null
            ? null
            : new Marker(variant, username, unpart(decoder, parts[2]), unpart(decoder, parts[3]));
      } catch (final RuntimeException e) {
        return null;
      }
    }

    private static String part(final Base64.Encoder encoder, final String value) {
      return value == null ? "" : encoder.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String unpart(final Base64.Decoder decoder, final String value) {
      return value.isEmpty() ? null : new String(decoder.decode(value), StandardCharsets.UTF_8);
    }
  }

  static void setMarker(
      final HttpServletRequest request, final HttpServletResponse response, final Marker marker) {
    response.addHeader(
        "Set-Cookie", cookieHeader(request, marker.encode(), COOKIE_MAX_AGE_SECONDS));
  }

  static void clearMarker(final HttpServletRequest request, final HttpServletResponse response) {
    response.addHeader("Set-Cookie", cookieHeader(request, "", 0));
  }

  static boolean hasMarker(final HttpServletRequest request) {
    return markerValue(request) != null;
  }

  static Marker readMarker(final HttpServletRequest request) {
    return Marker.decode(markerValue(request));
  }

  private static String markerValue(final HttpServletRequest request) {
    final var cookies = request.getCookies();
    if (cookies != null) {
      for (final Cookie cookie : cookies) {
        if (COOKIE_NAME.equals(cookie.getName())) {
          return cookie.getValue();
        }
      }
    }
    return null;
  }

  private static String cookieHeader(
      final HttpServletRequest request, final String value, final int maxAge) {
    final var builder =
        new StringBuilder(COOKIE_NAME)
            .append('=')
            .append(value)
            .append("; Path=")
            .append(contextRoot(request))
            .append("; Max-Age=")
            .append(maxAge)
            .append("; HttpOnly; SameSite=Lax");
    if (request.isSecure()) {
      builder.append("; Secure");
    }
    return builder.toString();
  }

  static String contextRoot(final HttpServletRequest request) {
    final var contextPath = request.getContextPath();
    return contextPath == null || contextPath.isEmpty() ? "/" : contextPath;
  }

  static void writePage(
      final HttpServletRequest request, final HttpServletResponse response, final Marker marker)
      throws IOException {
    final var details = new StringBuilder();
    if (marker.loginSource() != null || marker.managedBySource() != null) {
      details
          .append("<dt>Signed in with</dt><dd>")
          .append(escape(marker.loginSource() != null ? marker.loginSource() : "unknown"))
          .append("</dd><dt>Account managed by</dt><dd>")
          .append(escape(marker.managedBySource() != null ? marker.managedBySource() : "unknown"))
          .append("</dd>");
    }
    final var resetUrl =
        request.getContextPath() == null ? RESET_PATH : request.getContextPath() + RESET_PATH;
    final var page =
        PAGE_TEMPLATE
            .replace("{{details}}", details)
            .replace("{{title}}", escape(marker.variant().title))
            .replace("{{text}}", escape(marker.variant().text))
            .replace("{{username}}", escape(marker.username()))
            .replace("{{resetUrl}}", escape(resetUrl));
    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    response.setContentType("text/html;charset=UTF-8");
    response.setHeader("Cache-Control", "no-store");
    response.getWriter().write(page);
  }

  private static String escape(final String value) {
    return HtmlUtils.htmlEscape(value);
  }

  private static String loadTemplate() {
    try (final var in = new ClassPathResource("templates/blocked-login.html").getInputStream()) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static String pathWithinContext(final HttpServletRequest request) {
    final var path = request.getRequestURI();
    final var contextPath = request.getContextPath();
    return contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)
        ? path.substring(contextPath.length())
        : path;
  }

  static boolean isApiPath(final HttpServletRequest request) {
    final var candidate = pathWithinContext(request);
    final var unprefixed = withoutTenantPrefix(candidate);
    return SecurityPaths.API_PATHS.stream()
        .anyMatch(
            pattern ->
                PATH_MATCHER.match(pattern, candidate) || PATH_MATCHER.match(pattern, unprefixed));
  }

  static String withoutTenantPrefix(final String path) {
    return PATH_MATCHER.match(TENANT_PREFIX_PATTERN + "/**", path)
        ? path.substring(path.indexOf('/', "/physical-tenants/".length()))
        : path;
  }

  /**
   * Static webapp files (lazily imported chunks, styles, fonts, images) are never blocked: the
   * browser cannot render an error page for them, and a failed import breaks the SPA instead of
   * showing the blocked-login page. API paths are always checked.
   */
  static boolean isStaticResource(final HttpServletRequest request) {
    final var method = request.getMethod();
    if (!"GET".equals(method) && !"HEAD".equals(method)) {
      return false;
    }
    if (isApiPath(request)) {
      return false;
    }
    final var path = pathWithinContext(request);
    final var fileName = path.substring(path.lastIndexOf('/') + 1);
    final var dot = fileName.lastIndexOf('.');
    if (dot < 0) {
      return false;
    }
    final var extension = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    return STATIC_EXTENSIONS.contains(extension)
        || ("json".equals(extension) && PATH_MATCHER.match(ASSETS_PATTERN, path));
  }

  static boolean isBrowserNavigation(final HttpServletRequest request) {
    if (!"GET".equals(request.getMethod()) || isApiPath(request)) {
      return false;
    }
    final var fetchMode = request.getHeader("Sec-Fetch-Mode");
    if (fetchMode != null && !"navigate".equalsIgnoreCase(fetchMode)) {
      return false;
    }
    final var headers = Collections.list(request.getHeaders("Accept"));
    if (headers.isEmpty()) {
      return false;
    }
    try {
      final var types = MediaType.parseMediaTypes(headers);
      var preferred = types.get(0);
      for (final var type : types) {
        if (type.getQualityValue() > preferred.getQualityValue()) {
          preferred = type;
        }
      }
      return preferred.getQualityValue() > 0
          && "text".equalsIgnoreCase(preferred.getType())
          && "html".equalsIgnoreCase(preferred.getSubtype());
    } catch (final RuntimeException e) {
      return false;
    }
  }
}
