/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.security.tenant;

import static java.util.Collections.emptyList;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public interface TenantService {

  List<String> tenantsIds();

  AuthenticatedTenants getAuthenticatedTenants();

  boolean isTenantValid(final String tenantId);

  boolean isMultiTenancyEnabled();

  record AuthenticatedTenants(TenantAccessType tenantAccessType, List<String> ids) {

    public TenantAccessType getTenantAccessType() {
      return tenantAccessType;
    }

    public List<String> getTenantIds() {
      return ids;
    }

    public boolean contains(final String tenantId) {
      return ids.contains(tenantId);
    }

    public static AuthenticatedTenants allTenants() {
      return new AuthenticatedTenants(TenantAccessType.TENANT_ACCESS_ALL, emptyList());
    }

    public static AuthenticatedTenants noTenantsAssigned() {
      return new AuthenticatedTenants(TenantAccessType.TENANT_ACCESS_NONE, emptyList());
    }

    public static AuthenticatedTenants assignedTenants(final List<String> tenants) {
      return new AuthenticatedTenants(TenantAccessType.TENANT_ACCESS_ASSIGNED, tenants);
    }
  }

  enum TenantAccessType {
    TENANT_ACCESS_ALL,
    TENANT_ACCESS_ASSIGNED,
    TENANT_ACCESS_NONE
  }
}
