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
 * /api/ingestion/variable}) a bearer-only surface for clients of the public API audience, as the
 * legacy security stacks did.
 *
 * <p>Under CSL these paths share the API chain with the internal API, which also accepts the webapp
 * session, and which accepts the login audience and key set next to the public API ones. Their
 * handlers perform no per-user authorization, because they serve machine clients. So on these
 * paths, this filter answers {@code 401} unless the request carries a bearer token whose audience
 * contains the public API audience: {@code api.audience} for Self-Managed, the cloud API audience
 * for SaaS. These are the audiences that the legacy public API decoders validated.
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

  /**
   * @param publicApiAudience the audience a public API token must carry; when it is blank, every
   *     request on the public paths is denied, as the legacy audience validation did
   */
  public OptimizePublicApiAccessFilter(final String publicApiAudience) {
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
    if (SecurityContextHolder.getContext().getAuthentication()
        instanceof final JwtAuthenticationToken jwtAuthentication) {
      // a token without an 'aud' claim has a null audience list
      final List<String> audience = jwtAuthentication.getToken().getAudience();
      if (publicApiAudience != null && audience != null && audience.contains(publicApiAudience)) {
        filterChain.doFilter(request, response);
        return;
      }
      LOG.debug(
          "Denying bearer request at {}: the token's audience is not the public API audience",
          request.getRequestURI());
    } else {
      LOG.debug(
          "Denying request at {}: the public API accepts only a bearer token, not a session",
          request.getRequestURI());
    }
    response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
  }
}
