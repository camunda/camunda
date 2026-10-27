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
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Binds the request of the security filter chain to the {@link RequestContextHolder} and restores
 * the previous binding afterwards.
 *
 * <p>Spring's own request context filter runs before the chain, so the bound request is the one
 * without the HTTP session: CSL attaches the session inside each chain. A component that resolves
 * the current request to read the session, such as the resolution of the session's access token,
 * therefore finds no session. Every chain filter that runs after this one sees the request that
 * carries the session.
 */
public final class OptimizeRequestContextBindingFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final FilterChain filterChain)
      throws ServletException, IOException {
    final RequestAttributes previous = RequestContextHolder.getRequestAttributes();
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, response));
    try {
      filterChain.doFilter(request, response);
    } finally {
      RequestContextHolder.setRequestAttributes(previous);
    }
  }
}
