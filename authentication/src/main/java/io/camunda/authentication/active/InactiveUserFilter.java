/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.active;

import io.camunda.authentication.active.ActiveUserChecker.StoredUser;
import io.camunda.authentication.config.spi.SecurityPaths;
import io.camunda.authentication.source.LoginSources;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.spring.utils.PhysicalTenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.HtmlUtils;

/**
 * Rejects requests of authenticated users that are deactivated ({@code active=false}) with 403, and
 * requests whose login does not match the SCIM source that provisioned the user.
 *
 * <p>Login binding applies to users with a stored source when SCIM is enabled: the login must come
 * through the OIDC registration of that source, and the token claim configured as its {@code
 * login-binding-claim} must equal the stored {@code externalId}. A missing claim or an unknown
 * source (for example after renaming a source) fails the check, so the request is rejected. Clients
 * are never bound.
 *
 * <p>Must be registered with an order after Spring Security's filter chain proxy: filters ordered
 * after it run inside the proxy's call, where the {@code SecurityContext} of both bearer-token API
 * requests and session-based webapp requests is populated. Requests of clients (no username) and
 * unauthenticated requests pass untouched.
 */
public final class InactiveUserFilter extends OncePerRequestFilter {

  private static final Logger LOG = LoggerFactory.getLogger(InactiveUserFilter.class);
  private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
  private static final String TENANT_PREFIX_PATTERN = "/physical-tenants/*";
  private static final String PAGE_TEMPLATE = loadTemplate();
  private static final Set<String> STATIC_EXTENSIONS =
      Set.of(
          "js", "mjs", "css", "map", "png", "svg", "ico", "jpg", "jpeg", "gif", "webp", "woff",
          "woff2", "ttf");
  private static final String ASSETS_PATTERN = "/**/assets/**";

  private final CamundaAuthenticationProvider authenticationProvider;
  private final ActiveUserChecker activeUserChecker;
  private final LoginSources loginSources;
  private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

  public InactiveUserFilter(
      final CamundaAuthenticationProvider authenticationProvider,
      final ActiveUserChecker activeUserChecker) {
    this(authenticationProvider, activeUserChecker, LoginSources.disabled());
  }

  public InactiveUserFilter(
      final CamundaAuthenticationProvider authenticationProvider,
      final ActiveUserChecker activeUserChecker,
      final LoginSources loginSources) {
    this.authenticationProvider = authenticationProvider;
    this.activeUserChecker = activeUserChecker;
    this.loginSources = loginSources;
  }

  @Override
  protected void doFilterInternal(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final FilterChain filterChain)
      throws ServletException, IOException {
    if (isStaticResource(request)) {
      filterChain.doFilter(request, response);
      return;
    }
    final var authentication = authenticatedUser();
    if (authentication != null) {
      final var username = authentication.authenticatedUsername();
      final var tenant = physicalTenantId(request);
      if (loginSources.enabled()) {
        final var stored = activeUserChecker.user(tenant, username);
        if (!matchesProvisionedUser(authentication, stored)) {
          LOG.debug("Rejecting request of user '{}', the login does not match", username);
          block(
              request,
              response,
              "The login does not match the provisioned user.",
              "This sign-in can't be used",
              "You signed in through a different identity provider than the one that manages"
                  + " your Camunda account. Sign in again with the right provider, or contact"
                  + " your administrator.",
              username,
              LoginSources.loginSource(authentication.claims()),
              stored.source());
          return;
        }
      }
      if (!activeUserChecker.isActive(tenant, username)) {
        LOG.debug("Rejecting request of deactivated user '{}'", username);
        block(
            request,
            response,
            "The user is deactivated.",
            "Your account is deactivated",
            "Your identity provider has deactivated your account, so you can't use Camunda"
                + " right now. If you think this is a mistake, contact your administrator.",
            username,
            null,
            null);
        return;
      }
    }
    filterChain.doFilter(request, response);
  }

  private boolean matchesProvisionedUser(
      final CamundaAuthentication authentication, final StoredUser stored) {
    if (!stored.provisioned()) {
      return true;
    }
    final var source = loginSources.source(stored.source()).orElse(null);
    if (source == null) {
      return false;
    }
    final var claims = authentication.claims();
    if (source.oidcRegistration() != null
        && !source.id().equals(LoginSources.loginSource(claims))) {
      return false;
    }
    if (source.loginBindingClaim() != null) {
      final var claim = claims.get(source.loginBindingClaim());
      return claim != null
          && stored.externalId() != null
          && stored.externalId().equals(claim.toString());
    }
    return true;
  }

  private CamundaAuthentication authenticatedUser() {
    final var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || !authentication.isAuthenticated()
        || authentication instanceof AnonymousAuthenticationToken) {
      LOG.debug("Skipping active-user check, the request is not authenticated: {}", authentication);
      return null;
    }
    try {
      final var camundaAuthentication = authenticationProvider.getCamundaAuthentication();
      if (camundaAuthentication == null || camundaAuthentication.isAnonymous()) {
        return null;
      }
      final var username = camundaAuthentication.authenticatedUsername();
      return username == null || username.isBlank() ? null : camundaAuthentication;
    } catch (final RuntimeException e) {
      // Whoever needs the authentication downstream hits the same failure and reports it properly.
      LOG.debug("Skipping active-user check, the authentication could not be resolved", e);
      return null;
    }
  }

  private static String physicalTenantId(final HttpServletRequest request) {
    final var id = PhysicalTenantContext.getPhysicalTenantId(request);
    return id != null ? id : PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
  }

  private static boolean isBrowserNavigation(final HttpServletRequest request) {
    if (!"GET".equals(request.getMethod()) || isApiPath(request)) {
      return false;
    }
    final var fetchMode = request.getHeader("Sec-Fetch-Mode");
    if (fetchMode != null && !"navigate".equalsIgnoreCase(fetchMode)) {
      return false;
    }
    final var accept = request.getHeaders("Accept");
    final var headers = Collections.list(accept);
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

  private static String pathWithinContext(final HttpServletRequest request) {
    final var path = request.getRequestURI();
    final var contextPath = request.getContextPath();
    return contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)
        ? path.substring(contextPath.length())
        : path;
  }

  /**
   * Static webapp files (lazily imported chunks, styles, fonts, images) are never blocked: the
   * browser cannot render an error page for them, and a failed import breaks the SPA instead of
   * showing the blocked-login page. API paths are always checked.
   */
  private static boolean isStaticResource(final HttpServletRequest request) {
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

  private void block(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final String detail,
      final String title,
      final String text,
      final String username,
      final String usedSource,
      final String managingSource)
      throws IOException {
    final var hadSession = request.getSession(false) != null;
    if (hadSession) {
      endSession(request, response);
    }
    if (isBrowserNavigation(request)) {
      rejectWithPage(response, title, text, username, usedSource, managingSource);
    } else if (hadSession) {
      reject(response, request, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized", detail);
    } else {
      reject(response, request, HttpServletResponse.SC_FORBIDDEN, "Forbidden", detail);
    }
  }

  // Invalidating the HttpSession of a request that went through the session repository filter also
  // deletes the persistent web session from secondary storage and expires the session cookie.
  private void endSession(final HttpServletRequest request, final HttpServletResponse response) {
    try {
      logoutHandler.logout(
          request, response, SecurityContextHolder.getContext().getAuthentication());
    } catch (final RuntimeException e) {
      LOG.warn("Failed to end the session of a blocked user", e);
      SecurityContextHolder.clearContext();
    }
  }

  private static boolean isApiPath(final HttpServletRequest request) {
    final var candidate = pathWithinContext(request);
    final var unprefixed =
        PATH_MATCHER.match(TENANT_PREFIX_PATTERN + "/**", candidate)
            ? candidate.substring(candidate.indexOf('/', "/physical-tenants/".length()))
            : candidate;
    return SecurityPaths.API_PATHS.stream()
        .anyMatch(
            pattern ->
                PATH_MATCHER.match(pattern, candidate) || PATH_MATCHER.match(pattern, unprefixed));
  }

  private static void rejectWithPage(
      final HttpServletResponse response,
      final String title,
      final String text,
      final String username,
      final String usedSource,
      final String managingSource)
      throws IOException {
    final var details = new StringBuilder();
    if (usedSource != null || managingSource != null) {
      details
          .append("<dt>Signed in with</dt><dd>")
          .append(escape(usedSource != null ? usedSource : "unknown"))
          .append("</dd><dt>Account managed by</dt><dd>")
          .append(escape(managingSource != null ? managingSource : "unknown"))
          .append("</dd>");
    }
    final var page =
        PAGE_TEMPLATE
            .replace("{{details}}", details)
            .replace("{{title}}", escape(title))
            .replace("{{text}}", escape(text))
            .replace("{{username}}", escape(username));
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

  private static void reject(
      final HttpServletResponse response,
      final HttpServletRequest request,
      final int status,
      final String title,
      final String detail)
      throws IOException {
    response.setStatus(status);
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    final var instance = request.getRequestURI().replace("\\", "\\\\").replace("\"", "\\\"");
    response
        .getWriter()
        .write(
            "{\"type\":\"about:blank\",\"title\":\""
                + title
                + "\",\"status\":"
                + status
                + ",\"detail\":\""
                + detail
                + "\",\"instance\":\""
                + instance
                + "\"}");
  }
}
