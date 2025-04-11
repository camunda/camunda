/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.tenant;

import io.camunda.authentication.entity.CamundaPrincipal;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.service.TenantServices.TenantDTO;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;

@Component
public class TenantServiceImpl implements TenantService {

  @Autowired private SecurityConfiguration securityConfiguration;

  @Override
  public List<String> tenantsIds() {
    final List<String> authenticatedTenantIds = new ArrayList<>();
    final var requestAuthentication = SecurityContextHolder.getContext().getAuthentication();
    if (requestAuthentication.getPrincipal()
        instanceof final CamundaPrincipal authenticatedPrincipal) {

      authenticatedTenantIds.addAll(
          authenticatedPrincipal.getAuthenticationContext().tenants().stream()
              .map(TenantDTO::tenantId)
              .toList());
    }
    return authenticatedTenantIds;
  }

  @Override
  public AuthenticatedTenants getAuthenticatedTenants() {
    if (!isMultiTenancyEnabled() || RequestContextHolder.getRequestAttributes() == null) {
      // If the query comes from the source without request context OR
      // Multitenancy is not enabled, return all tenants
      return AuthenticatedTenants.allTenants();
    }

    final List<String> tenants = tenantsIds();

    if (CollectionUtils.isNotEmpty(tenants)) {
      return AuthenticatedTenants.assignedTenants(tenants);
    } else {
      return AuthenticatedTenants.noTenantsAssigned();
    }
  }

  @Override
  public boolean isTenantValid(final String tenantId) {
    if (isMultiTenancyEnabled()) {
      return getAuthenticatedTenants().contains(tenantId);
    } else {
      return true;
    }
  }

  @Override
  public boolean isMultiTenancyEnabled() {
    return securityConfiguration.getMultiTenancy().isEnabled();
  }
}
