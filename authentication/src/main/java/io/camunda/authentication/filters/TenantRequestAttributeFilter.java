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
import io.camunda.search.entities.TenantEntity;
import io.camunda.search.query.TenantQuery;
import io.camunda.service.TenantServices;
import io.camunda.zeebe.gateway.impl.configuration.MultiTenancyCfg;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.Principal;
import java.util.List;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.client.authentication.OAuth2LoginAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class TenantRequestAttributeFilter extends OncePerRequestFilter {
  private static final Logger LOG = LoggerFactory.getLogger(TenantRequestAttributeFilter.class);

  private final TenantServices tenantServices;
  private final MultiTenancyCfg multiTenancyCfg;

  public TenantRequestAttributeFilter(
      final TenantServices tenantServices, final MultiTenancyCfg multiTenancyCfg) {
    this.tenantServices = tenantServices;
    this.multiTenancyCfg = multiTenancyCfg;
  }

  @Override
  protected void doFilterInternal(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final FilterChain filterChain)
      throws ServletException, IOException {
    final Principal userPrincipal = request.getUserPrincipal();

    final CamundaUser user = getCamundaUser(userPrincipal);
    if (user == null) {
      response.sendError(HttpStatus.UNAUTHORIZED.value());
      return;
    }
    final List<String> tenantIds = getTenantIds(user.getUserKey());
    LOG.debug("Authenticated tenants: {}", tenantIds);
    TenantAttributeHolder.setTenantIds(tenantIds);
    filterChain.doFilter(request, response);
  }

  private @Nullable CamundaUser getCamundaUser(final Principal principal) {
    return switch (principal) {
      case final UsernamePasswordAuthenticationToken token -> {
        if (!(token.getPrincipal() instanceof final CamundaUser user)) {
          LOG.error("cannot find tenants: principal is not a camunda user");
          yield null;
        }
        yield user;
      }
      case final OAuth2LoginAuthenticationToken token -> {
        if (!(token.getPrincipal() instanceof final CamundaUser user)) {
          LOG.error("cannot find tenants: principal is not a camunda user");
          yield null;
        }
        yield user;
      }
      default -> {
        LOG.error(
            "cannot find tenants: unsupported principal type {}", principal.getClass().getName());
        yield null;
      }
    };
  }

  private List<String> getTenantIds(final Long userKey) {
    if (!multiTenancyCfg.isEnabled()) {
      return List.of(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    }
    try {
      return tenantServices
          .search(
              TenantQuery.of(
                  queryBuilder ->
                      queryBuilder.filter(filterBuilder -> filterBuilder.userKey(userKey))))
          .items()
          .stream()
          .map(TenantEntity::tenantId)
          .toList();

    } catch (final RuntimeException e) {
      LOG.error("cannot find tenants: %s".formatted(e.getMessage()), e);
      throw new InternalAuthenticationServiceException("Cannot find tenants", e);
    }
  }
}
