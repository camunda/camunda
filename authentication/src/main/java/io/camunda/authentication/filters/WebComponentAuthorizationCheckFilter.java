/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.filters;

import static io.camunda.service.authorization.Authorizations.COMPONENT_ACCESS_AUTHORIZATION;

import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.reader.ResourceAccessProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

public class WebComponentAuthorizationCheckFilter extends OncePerRequestFilter {

  private static final Logger LOG =
      LoggerFactory.getLogger(WebComponentAuthorizationCheckFilter.class);
  private static final List<String> WEB_COMPONENTS =
      List.of("identity", "admin", "operate", "tasklist");
  private static final List<String> STATIC_RESOURCES =
      List.of(".css", ".js", ".js.map", ".jpg", ".png", "woff2", ".ico", ".svg");
  final UrlPathHelper urlPathHelper = new UrlPathHelper();
  private final SecurityConfiguration securityConfig;
  private final CamundaAuthenticationProvider authenticationProvider;
  private final ResourceAccessProvider resourceAccessProvider;

  public WebComponentAuthorizationCheckFilter(
      final SecurityConfiguration securityConfig,
      final CamundaAuthenticationProvider authenticationProvider,
      final ResourceAccessProvider resourceAccessProvider) {
    this.securityConfig = securityConfig;
    this.authenticationProvider = authenticationProvider;
    this.resourceAccessProvider = resourceAccessProvider;
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
          String.format("%s/%s/forbidden", request.getContextPath(), findWebComponent(request)));
      return;
    }
    filterChain.doFilter(request, response);
  }

  private boolean isAllowed(final HttpServletRequest request) {

    if (!securityConfig.getAuthorizations().isEnabled() || !isAuthenticated()) {
      return true;
    }

    if (request.getRequestURL().toString().endsWith("/forbidden")) {
      return true;
    }

    if (isStaticResource(request)) {
      return true;
    }

    final String component = findWebComponent(request);
    if (component == null) {
      return true;
    }

    return hasAccessToComponent(component);
  }

  private boolean hasAccessToComponent(final String component) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    if ("admin".equals(component) || "identity".equals(component)) {
      return resourceAccessProvider
              .hasResourceAccessByResourceId(
                  authentication, COMPONENT_ACCESS_AUTHORIZATION, "identity")
              .allowed()
          || resourceAccessProvider
              .hasResourceAccessByResourceId(
                  authentication, COMPONENT_ACCESS_AUTHORIZATION, "admin")
              .allowed();
    }
    return resourceAccessProvider
        .hasResourceAccessByResourceId(authentication, COMPONENT_ACCESS_AUTHORIZATION, component)
        .allowed();
  }

  private boolean isStaticResource(final HttpServletRequest request) {
    final String requestUri = request.getRequestURI();
    return STATIC_RESOURCES.stream().anyMatch(requestUri::endsWith);
  }

  private String findWebComponent(final HttpServletRequest request) {
    final String componentPath =
        urlPathHelper.getPathWithinApplication(request).substring(1).split("/")[0];
    return WEB_COMPONENTS.stream().filter(componentPath::equals).findFirst().orElse(null);
  }

  private boolean isAuthenticated() {
    final var auth = SecurityContextHolder.getContext().getAuthentication();
    return auth != null && auth.isAuthenticated();
  }
}
