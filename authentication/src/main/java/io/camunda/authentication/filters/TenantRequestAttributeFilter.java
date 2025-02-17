/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.filters;

import io.camunda.authentication.entity.CamundaPrincipal;
import io.camunda.authentication.tenant.TenantAttributeHolder;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.service.TenantServices.TenantDTO;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.Principal;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.web.filter.OncePerRequestFilter;

public class TenantRequestAttributeFilter extends OncePerRequestFilter {
  private static final Logger LOG = LoggerFactory.getLogger(TenantRequestAttributeFilter.class);

  private final MultiTenancyConfiguration multiTenancyCfg;

  public TenantRequestAttributeFilter(final MultiTenancyConfiguration multiTenancyCfg) {
    this.multiTenancyCfg = multiTenancyCfg;
  }

  @Override
  protected void doFilterInternal(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final FilterChain filterChain)
      throws ServletException, IOException {
    final Set<String> tenantIds = getTenantIds(request.getUserPrincipal());
    if (tenantIds == null) {
      throw new InternalAuthenticationServiceException("cannot find tenants for request");
    }
    LOG.debug("Authenticated tenants: {}", tenantIds);
    TenantAttributeHolder.setTenantIds(tenantIds);
    filterChain.doFilter(request, response);
  }

  private @Nullable Set<String> getTenantIds(final Principal principal) {
    if (!multiTenancyCfg.isEnabled()) {
      return Set.of(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    }
    if (!(principal instanceof final Authentication auth)) {
      LOG.error(
          "cannot find tenants: unsupported principal type: {}", principal.getClass().getName());
      return null;
    }
    if (!(auth.getPrincipal() instanceof final CamundaPrincipal camundaPrincipal)) {
      LOG.error("cannot find tenants: unsupported principal type: {}", auth.getClass().getName());
      return null;
    }
    return camundaPrincipal.getAuthenticationContext().tenants().stream()
        .map(TenantDTO::tenantId)
        .collect(Collectors.toSet());
  }
}
