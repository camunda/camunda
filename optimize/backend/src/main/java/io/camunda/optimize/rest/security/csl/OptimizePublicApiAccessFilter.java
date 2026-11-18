/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import io.camunda.security.api.context.CamundaAuthenticationProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Keeps the public API ({@code /api/public/**}) and the variable ingestion endpoint ({@code
 * /api/ingestion/variable}) a surface for API clients only, as the legacy security stacks did.
 *
 * <p>Under CSL these paths share the API chain with the internal API, which also accepts the webapp
 * session and users' bearer tokens. Their handlers perform no per-user authorization, because they
 * serve machine clients. So on these paths, this filter answers {@code 401} unless the request
 * carries a bearer token that CSL classifies as an API client's, by the configured {@code
 * camunda.security.authentication.oidc.client-id-claim}. In SaaS, the token must also carry this
 * cluster's id.
 *
 * <p>The client classification replaces an audience check: SaaS no longer passes Optimize a public
 * API audience once it runs CSL, so that check denied every API client (camunda/camunda#69726).
 */
public final class OptimizePublicApiAccessFilter extends OncePerRequestFilter {

  /** Also listed in {@link OptimizeSecurityPathAdapter#apiPaths()}, which must keep both. */
  static final String PUBLIC_API_PATTERN = "/api/public/**";

  static final String INGESTION_VARIABLE_PATTERN = "/api/ingestion/variable";

  private static final Logger LOG = LoggerFactory.getLogger(OptimizePublicApiAccessFilter.class);

  /**
   * Spring's path patterns decode each path segment before they match, as the request mapping does,
   * so an encoded path such as {@code /api/p%75blic/collection} cannot skip this filter. The
   * ingestion pattern does not match a trailing slash; that is safe while trailing-slash matching
   * stays off in Spring MVC, its default since Spring 6.
   */
  private static final RequestMatcher PUBLIC_API_PATHS =
      new OrRequestMatcher(
          PathPatternRequestMatcher.withDefaults().matcher(PUBLIC_API_PATTERN),
          PathPatternRequestMatcher.withDefaults().matcher(INGESTION_VARIABLE_PATTERN));

  private final CamundaAuthenticationProvider authenticationProvider;
  private final String clusterId;

  private OptimizePublicApiAccessFilter(
      final CamundaAuthenticationProvider authenticationProvider, final String clusterId) {
    this.authenticationProvider =
        Objects.requireNonNull(authenticationProvider, "authenticationProvider must not be null");
    this.clusterId = clusterId;
  }

  /**
   * @param authenticationProvider CSL's classification of the request's token as a user's or an API
   *     client's
   */
  public static OptimizePublicApiAccessFilter forSelfManaged(
      final CamundaAuthenticationProvider authenticationProvider) {
    return new OptimizePublicApiAccessFilter(authenticationProvider, null);
  }

  /**
   * @param authenticationProvider see {@link #forSelfManaged(CamundaAuthenticationProvider)}
   * @param clusterId the cluster id a public API token must carry in its cluster id claim. The
   *     shared chain accepts a token without the claim, because the login token has none; the
   *     legacy public API decoder required it.
   */
  public static OptimizePublicApiAccessFilter forSaas(
      final CamundaAuthenticationProvider authenticationProvider, final String clusterId) {
    return new OptimizePublicApiAccessFilter(
        authenticationProvider, Objects.requireNonNull(clusterId, "clusterId must not be null"));
  }

  @Override
  protected boolean shouldNotFilter(final HttpServletRequest request) {
    return !PUBLIC_API_PATHS.matches(request);
  }

  @Override
  protected void doFilterInternal(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final FilterChain filterChain)
      throws ServletException, IOException {
    final Denial denial = check();
    if (denial == null) {
      filterChain.doFilter(request, response);
      return;
    }
    // A denial is an expected client error (wrong or missing token), so it logs at DEBUG, as the
    // sibling OptimizeBearerPermissionFilter does.
    LOG.debug("Denying request at {}: {}", request.getRequestURI(), denial.reason);
    response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
  }

  private Denial check() {
    if (!(SecurityContextHolder.getContext().getAuthentication()
        instanceof final JwtAuthenticationToken jwtAuthentication)) {
      return Denial.NOT_A_BEARER_TOKEN;
    }
    if (!isApiClient()) {
      return Denial.NOT_AN_API_CLIENT;
    }
    if (clusterId != null
        && !clusterId.equals(
            jwtAuthentication
                .getToken()
                .getClaims()
                .get(OptimizeCloudClusterValidator.CLUSTER_CLAIM))) {
      return Denial.WRONG_CLUSTER;
    }
    return null;
  }

  // Reads CSL's own classification, as OptimizeBearerPermissionFilter does, so the two filters and
  // every other CSL consumer agree on who is a client. A token CSL cannot classify is denied.
  private boolean isApiClient() {
    try {
      final var camundaAuthentication = authenticationProvider.getCamundaAuthentication();
      return camundaAuthentication != null && camundaAuthentication.authenticatedClientId() != null;
    } catch (final RuntimeException e) {
      LOG.debug("Could not classify the bearer token's subject, denying the public API request", e);
      return false;
    }
  }

  private enum Denial {
    NOT_A_BEARER_TOKEN("the public API accepts only a bearer token, not a session"),
    NOT_AN_API_CLIENT(
        "the token is not an API client's: it does not carry the configured client id claim"),
    WRONG_CLUSTER("the token does not carry this cluster's id");

    private final String reason;

    Denial(final String reason) {
      this.reason = reason;
    }
  }
}
