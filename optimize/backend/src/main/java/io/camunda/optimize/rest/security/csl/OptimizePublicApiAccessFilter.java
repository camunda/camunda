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

  private static final Logger LOG = LoggerFactory.getLogger(OptimizePublicApiAccessFilter.class);
  private static final String PUBLIC_API_PATH = "/api/public";
  private static final String INGESTION_VARIABLE_PATH = "/api/ingestion/variable";

  private final String publicApiAudience;

  /**
   * @param publicApiAudience the audience a public API token must carry; when it is blank, every
   *     request on the public paths is denied, as the legacy audience validation did
   */
  public OptimizePublicApiAccessFilter(final String publicApiAudience) {
    this.publicApiAudience = publicApiAudience;
    if (publicApiAudience == null || publicApiAudience.isBlank()) {
      LOG.warn(
          "No public API audience is configured, so every request to {} and {} is denied."
              + " Configure the audience that the public API clients' tokens carry.",
          PUBLIC_API_PATH + "/**",
          INGESTION_VARIABLE_PATH);
    }
  }

  @Override
  protected boolean shouldNotFilter(final HttpServletRequest request) {
    final String path = request.getRequestURI().substring(request.getContextPath().length());
    return !(path.equals(PUBLIC_API_PATH)
        || path.startsWith(PUBLIC_API_PATH + "/")
        || path.equals(INGESTION_VARIABLE_PATH));
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
