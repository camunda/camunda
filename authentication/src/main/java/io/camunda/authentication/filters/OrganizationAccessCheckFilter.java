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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class OrganizationAccessCheckFilter extends OncePerRequestFilter {
  private static final Logger LOG = LoggerFactory.getLogger(OrganizationAccessCheckFilter.class);

  private final String requiredOrganizationId;

  public OrganizationAccessCheckFilter(final String requiredOrganizationId) {
    this.requiredOrganizationId = requiredOrganizationId;
  }

  @Override
  protected void doFilterInternal(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final FilterChain filterChain)
      throws ServletException, IOException {
    final var principal = request.getUserPrincipal();
    if (principal == null) {
      filterChain.doFilter(request, response);
      return;
    }

    final CamundaPrincipal camundaPrincipal = findCurrentCamundaPrincipal();
    if (camundaPrincipal == null) {
      LOG.info("cannot verify required organization id: no camunda principal");
    } else if (camundaPrincipal.getOrganizationIds() == null) {
      filterChain.doFilter(request, response);
      return;
    } else if (!camundaPrincipal.getOrganizationIds().contains(requiredOrganizationId)) {
      LOG.info("principal doesn't belong to required organization '{}'", requiredOrganizationId);
    } else {
      filterChain.doFilter(request, response);
      return;
    }
    response.setStatus(HttpStatus.UNAUTHORIZED.value());
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
