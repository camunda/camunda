/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import io.camunda.authentication.service.PermissionService;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.security.core.context.SecurityContextHolder;

public class CamundaApplicationFilter implements Filter {

  private static final List<String> APPLICATIONS = List.of("/tasklist", "/operate");
  private static final Pattern APP_PREFIX_PATTERN = Pattern.compile("/(tasklist|operate).*");

  private final PermissionService permissionService;

  public CamundaApplicationFilter(final PermissionService permissionService) {
    this.permissionService = permissionService;
  }

  @Override
  public void doFilter(
      final ServletRequest request, final ServletResponse response, final FilterChain chain)
      throws IOException, ServletException {

    final var httpRequest = (HttpServletRequest) request;
    final var httpResponse = (HttpServletResponse) response;

    final var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.isAuthenticated()) {

      final var applicationName = getApplicationIfPresent(httpRequest);
      if (applicationName != null && !hasPermissionToAccessApplication(applicationName)) {
        httpResponse.sendError(403, "No access rights for " + applicationName);
        return;
      }
    }

    chain.doFilter(request, response);
  }

  private String getApplicationIfPresent(final HttpServletRequest request) {
    final var requestURI = request.getRequestURI();
    final var contextPath = request.getContextPath();
    final var requestURL = requestURI.substring(contextPath.length());
    //    return APPLICATIONS.stream().filter(requestURL::startsWith).findFirst().orElse(null);

    final var matcher = APP_PREFIX_PATTERN.matcher(requestURL);
    if (matcher.hasMatch()) {
      return matcher.group(1);
    }
    return null;
  }

  private boolean hasPermissionToAccessApplication(final String applicationName) {
    return permissionService.hasPermissionToAccessApplication(applicationName);
  }
}
