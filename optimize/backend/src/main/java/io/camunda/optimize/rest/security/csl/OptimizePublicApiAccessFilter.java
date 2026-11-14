/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Keeps the public API ({@code /api/public/**}) and the variable ingestion endpoint ({@code
 * /api/ingestion/variable}) a bearer-only surface for clients of the public API audience, as the
 * legacy security stacks did.
 *
 * <p>Under CSL these paths share the API chain with the internal API, which also accepts the webapp
 * session, and which accepts the login audience and key set next to the public API ones. Their
 * handlers perform no per-user authorization, because they serve machine clients. So on these
 * paths, this filter answers {@code 401} unless the request carries a bearer token whose audience
 * contains the public API audience: {@code api.audience} for Self-Managed, the cloud API audience
 * for SaaS. In SaaS, the token must also carry this cluster's id. These are the checks that the
 * legacy public API decoders made.
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

  private final String publicApiAudience;
  private final String clusterId;
  // The first denial for each reason logs a WARN, so a broken client or configuration shows up at
  // the default log level; later ones log at DEBUG.
  private final Set<Denial> reportedDenials = ConcurrentHashMap.newKeySet();

  private OptimizePublicApiAccessFilter(final String publicApiAudience, final String clusterId) {
    if (publicApiAudience == null || publicApiAudience.isBlank()) {
      this.publicApiAudience = null;
      LOG.warn(
          "No public API audience is configured, so every request to {} and {} is denied."
              + " Configure the audience that the public API clients' tokens carry.",
          PUBLIC_API_PATTERN,
          INGESTION_VARIABLE_PATTERN);
    } else {
      this.publicApiAudience = publicApiAudience;
    }
    this.clusterId = clusterId;
  }

  /**
   * @param publicApiAudience the audience a public API token must carry; when it is blank, every
   *     request on the public paths is denied, as the legacy audience validation did
   */
  public static OptimizePublicApiAccessFilter forSelfManaged(final String publicApiAudience) {
    return new OptimizePublicApiAccessFilter(publicApiAudience, null);
  }

  /**
   * @param publicApiAudience see {@link #forSelfManaged(String)}
   * @param clusterId the cluster id a public API token must carry in its cluster id claim. The
   *     shared chain accepts a token without the claim, because the login token has none; the
   *     legacy public API decoder required it.
   */
  public static OptimizePublicApiAccessFilter forSaas(
      final String publicApiAudience, final String clusterId) {
    return new OptimizePublicApiAccessFilter(
        publicApiAudience, Objects.requireNonNull(clusterId, "clusterId must not be null"));
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
    if (reportedDenials.add(denial)) {
      LOG.warn(
          "Denying request at {}: {}. Later denials for this reason are logged at DEBUG.",
          request.getRequestURI(),
          denial.reason);
    } else {
      LOG.debug("Denying request at {}: {}", request.getRequestURI(), denial.reason);
    }
    response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
  }

  private Denial check() {
    if (!(SecurityContextHolder.getContext().getAuthentication()
        instanceof final JwtAuthenticationToken jwtAuthentication)) {
      return Denial.NOT_A_BEARER_TOKEN;
    }
    if (publicApiAudience == null) {
      return Denial.NO_AUDIENCE_CONFIGURED;
    }
    final Jwt token = jwtAuthentication.getToken();
    // a token without an 'aud' claim has a null audience list
    final List<String> audience = token.getAudience();
    if (audience == null || !audience.contains(publicApiAudience)) {
      return Denial.WRONG_AUDIENCE;
    }
    if (clusterId != null
        && !clusterId.equals(token.getClaims().get(OptimizeCloudClusterValidator.CLUSTER_CLAIM))) {
      return Denial.WRONG_CLUSTER;
    }
    return null;
  }

  private enum Denial {
    NOT_A_BEARER_TOKEN("the public API accepts only a bearer token, not a session"),
    NO_AUDIENCE_CONFIGURED("no public API audience is configured"),
    WRONG_AUDIENCE("the token's audience is not the public API audience"),
    WRONG_CLUSTER("the token does not carry this cluster's id");

    private final String reason;

    Denial(final String reason) {
      this.reason = reason;
    }
  }
}
