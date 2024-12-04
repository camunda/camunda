/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.filters;

import io.camunda.authentication.entity.CamundaUser;
import io.camunda.authentication.tenant.TenantAttributeHolder;
import io.camunda.service.TenantServices.TenantDTO;
import io.camunda.zeebe.gateway.impl.configuration.MultiTenancyCfg;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

public class TenantRequestAttributeFilter extends OncePerRequestFilter {
  private static final Logger LOG = LoggerFactory.getLogger(TenantRequestAttributeFilter.class);

  private final MultiTenancyCfg multiTenancyCfg;

  public TenantRequestAttributeFilter(final MultiTenancyCfg multiTenancyCfg) {
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

    return switch (principal) {
      case final UsernamePasswordAuthenticationToken token -> {
        if (!(token.getPrincipal() instanceof final CamundaUser user)) {
          LOG.error("cannot find tenants: principal is not a camunda user");
          yield null;
        }
        yield user.getTenants().stream().map(TenantDTO::tenantId).collect(Collectors.toSet());
      }
      case final OAuth2AuthenticationToken token -> {
        LOG.error("cannot find tenants: tenant mapping isn't implemented for oidc");
        yield null;
      }
      default -> {
        LOG.error(
            "cannot find tenants: unsupported principal type {}", principal.getClass().getName());
        yield null;
      }
    };
  }
}
