/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.filters;

import io.camunda.authentication.entity.CamundaPrincipal;
import io.camunda.security.configuration.SecurityConfiguration;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

public class WebApplicationAuthorizationCheckFilter extends OncePerRequestFilter {

  private static final Logger LOG =
      LoggerFactory.getLogger(WebApplicationAuthorizationCheckFilter.class);
  private static final List<String> WEB_APPLICATIONS = List.of("identity", "operate", "tasklist");
  private static final List<String> STATIC_RESOURCES =
      List.of(".css", ".js", ".jpg", ".png", "woff2", ".ico", ".svg");
  final UrlPathHelper urlPathHelper = new UrlPathHelper();
  private final SecurityConfiguration securityConfig;

  public WebApplicationAuthorizationCheckFilter(final SecurityConfiguration securityConfig) {
    this.securityConfig = securityConfig;
  }

  @Override
  protected void doFilterInternal(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final FilterChain filterChain)
      throws ServletException, IOException {

    if (!isAllowed(request)) {
      LOG.warn("Access denied for request: {}", request.getRequestURI());
      response.sendRedirect(
          String.format("%s/%s/forbidden", request.getContextPath(), findWebApplication(request)));
      return;
    }
    filterChain.doFilter(request, response);
  }

  private boolean isAllowed(final HttpServletRequest request) {

    if (!securityConfig.getAuthorizations().isEnabled()) {
      return true;
    }

    if (request.getRequestURL().toString().endsWith("/forbidden")) {
      return true;
    }

    if (isStaticResource(request)) {
      return true;
    }

    final String application = findWebApplication(request);
    if (application == null) {
      return true;
    }

    final CamundaPrincipal principal = findCurrentCamundaPrincipal();
    return principal == null
        || principal.getAuthenticationContext().authorizedApplications().contains(application)
        || principal.getAuthenticationContext().authorizedApplications().contains("*");
  }

  private boolean isStaticResource(final HttpServletRequest request) {
    final String requestUri = request.getRequestURI();
    return STATIC_RESOURCES.stream().anyMatch(requestUri::endsWith);
  }

  private String findWebApplication(final HttpServletRequest request) {
    final String applicationPath =
        urlPathHelper.getPathWithinApplication(request).substring(1).split("/")[0];
    return WEB_APPLICATIONS.stream().filter(applicationPath::equals).findFirst().orElse(null);
  }

  private CamundaPrincipal findCurrentCamundaPrincipal() {
    final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null
        && auth.isAuthenticated()
        && auth.getPrincipal() instanceof final CamundaPrincipal principal) {
      return principal;
    }
    return null;
  }
}
