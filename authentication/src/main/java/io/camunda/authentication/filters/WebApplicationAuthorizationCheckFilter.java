/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.filters;

import io.camunda.authentication.entity.CamundaPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class WebApplicationAuthorizationCheckFilter extends OncePerRequestFilter {

  private static final List<String> webApplications = List.of("identity", "operate", "tasklist");
  private static final List<String> staticResources = List.of(".css", ".js", ".jpg", ".png");

  @Override
  protected void doFilterInternal(
      final HttpServletRequest request,
      final @NotNull HttpServletResponse response,
      final @NotNull FilterChain filterChain)
      throws ServletException, IOException {

    if (!isStaticResource(request)) {
      final Optional<String> application = extractApplication(request);
      if (application.isPresent()) {
        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof final CamundaPrincipal principal) {
          if (!principal
              .getAuthenticationContext()
              .authorizedApplications()
              .contains(application.get())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
          }
        }
      }
    }
    filterChain.doFilter(request, response);
  }

  private static boolean isStaticResource(final HttpServletRequest request) {
    return staticResources.stream()
        .anyMatch(resource -> request.getRequestURI().endsWith(resource));
  }

  public Optional<String> extractApplication(final HttpServletRequest request) {
    return webApplications.stream()
        .filter(wa -> request.getRequestURL().toString().contains(wa + "/"))
        .findFirst();
  }
}
