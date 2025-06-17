/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.security.configuration.SecurityConfiguration;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ApiFeatureFlagFilter extends HttpFilter {
  private final SecurityConfiguration securityConfig;

  public ApiFeatureFlagFilter(
      @Autowired(required = false) final SecurityConfiguration securityConfig) {
    this.securityConfig = securityConfig;
  }

  @Override
  protected void doFilter(
      final HttpServletRequest req, final HttpServletResponse res, final FilterChain chain)
      throws IOException, ServletException {
    if (req.getRequestURI().contains("/v2/groups")) {
      if (checkGroupsClaimEnabled()) {
        res.setStatus(HttpServletResponse.SC_FORBIDDEN);
        res.setContentType("application/json");
        res.getWriter()
            .write(
                generateAccessDeniedResponse(
                    req, "Due to security configuration, groups's endpoints are not accessible"));
        return;
      }
    }
    super.doFilter(req, res, chain);
  }

  private static @NotNull String generateAccessDeniedResponse(
      final HttpServletRequest req, final String message) {
    return """
        {
           "type": "about:blank",
           "status": 403,
           "title": "Access issue",
           "detail": "%s",
           "instance": "%s"
        }
        """
        .formatted(message, req.getRequestURI());
  }

  private boolean checkGroupsClaimEnabled() {
    return securityConfig != null
        && securityConfig.getAuthentication() != null
        && securityConfig.getAuthentication().getOidc() != null
        && securityConfig.getAuthentication().getOidc().getGroupsClaim() != null
        && !securityConfig.getAuthentication().getOidc().getGroupsClaim().isEmpty();
  }
}
