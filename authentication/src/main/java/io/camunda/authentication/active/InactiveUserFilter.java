/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.active;

import io.camunda.authentication.active.ActiveUserChecker.StoredUser;
import io.camunda.authentication.active.BlockedLogin.Marker;
import io.camunda.authentication.active.BlockedLogin.Variant;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.filter.OncePerRequestFilter;

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
    if (BlockedLogin.isStaticResource(request)) {
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
              new Marker(
                  Variant.MISMATCH,
                  username,
                  LoginSources.loginSource(authentication.claims()),
                  stored.source()));
          return;
        }
      }
      if (!activeUserChecker.isActive(tenant, username)) {
        LOG.debug("Rejecting request of deactivated user '{}'", username);
        block(
            request,
            response,
            "The user is deactivated.",
            new Marker(Variant.DEACTIVATED, username, null, null));
        return;
      }
      if (BlockedLogin.hasMarker(request)) {
        BlockedLogin.clearMarker(request, response);
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

  private void block(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final String detail,
      final Marker marker)
      throws IOException {
    final var hadSession = request.getSession(false) != null;
    if (hadSession) {
      endSession(request, response);
      BlockedLogin.setMarker(request, response, marker);
    }
    if (BlockedLogin.isBrowserNavigation(request)) {
      BlockedLogin.writePage(request, response, marker);
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
