/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security.tenant;

import static io.camunda.webapps.schema.entities.AbstractExporterEntity.DEFAULT_TENANT_ID;

import io.camunda.authentication.entity.CamundaPrincipal;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.service.TenantServices.TenantDTO;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;

@Component
public class TenantService {

  @Autowired private SecurityConfiguration securityConfiguration;

  public List<String> tenantIds() {
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

  public AuthenticatedTenants getAuthenticatedTenants() {
    if (RequestContextHolder.getRequestAttributes() == null) {
      // if the query comes from the source without request context, return all tenants
      return AuthenticatedTenants.allTenants();
    }

    if (!isMultiTenancyEnabled()) {
      // the user/app has access to only <default> tenant
      return AuthenticatedTenants.assignedTenants(List.of(DEFAULT_TENANT_ID));
    }

    final var tenants = tenantIds();

    if (tenants != null && !tenants.isEmpty()) {
      return AuthenticatedTenants.assignedTenants(tenants);
    } else {
      return AuthenticatedTenants.noTenantsAssigned();
    }
  }

  private boolean isMultiTenancyEnabled() {
    return securityConfiguration.getMultiTenancy().isEnabled();
  }

  public static final class AuthenticatedTenants {

    private final TenantAccessType tenantAccessType;
    private final List<String> ids;

    private AuthenticatedTenants(final TenantAccessType tenantAccessType, final List<String> ids) {
      this.tenantAccessType = tenantAccessType;
      this.ids = ids;
    }

    public static AuthenticatedTenants allTenants() {
      return new AuthenticatedTenants(TenantAccessType.TENANT_ACCESS_ALL, null);
    }

    public static AuthenticatedTenants noTenantsAssigned() {
      return new AuthenticatedTenants(TenantAccessType.TENANT_ACCESS_NONE, null);
    }

    public static AuthenticatedTenants assignedTenants(final List<String> tenants) {
      return new AuthenticatedTenants(TenantAccessType.TENANT_ACCESS_ASSIGNED, tenants);
    }

    public TenantAccessType getTenantAccessType() {
      return tenantAccessType;
    }

    public List<String> getTenantIds() {
      return ids;
    }
  }

  public enum TenantAccessType {
    TENANT_ACCESS_ALL,
    TENANT_ACCESS_ASSIGNED,
    TENANT_ACCESS_NONE
  }
}
