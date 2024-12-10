/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity;

import io.camunda.migration.identity.dto.Tenant;
import io.camunda.migration.identity.midentity.ManagementIdentityClient;
import io.camunda.migration.identity.midentity.ManagementIdentityTransformer;
import io.camunda.migration.identity.midentity.MigrationStatusUpdateRequest;
import io.camunda.migration.identity.service.TenantService;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TenantMigrationHandler implements MigrationHandler {

  public static final int SIZE = 2;
  private final ManagementIdentityClient managementIdentityClient;
  private final ManagementIdentityTransformer managementIdentityTransformer;
  private final TenantService tenantService;

  public TenantMigrationHandler(
      final ManagementIdentityClient managementIdentityClient,
      final ManagementIdentityTransformer managementIdentityTransformer,
      final TenantService tenantService) {
    this.managementIdentityClient = managementIdentityClient;
    this.managementIdentityTransformer = managementIdentityTransformer;
    this.tenantService = tenantService;
  }

  @Override
  public void migrate() {
    Tenant lastTenant = null;
    do {
      final List<Tenant> tenants = managementIdentityClient.fetchTenants(lastTenant, SIZE);
      managementIdentityClient.updateMigrationStatus(
          tenants.stream().map(this::createTenant).toList());
      lastTenant = tenants.isEmpty() ? null : tenants.get(tenants.size() - 1);
    } while (lastTenant != null);
  }

  private MigrationStatusUpdateRequest createTenant(final Tenant tenant) {
    try {
      tenantService.fetchOrCreateTenant(tenant.tenantId(), tenant.name());
    } catch (final Exception e) {
      if (!isConflictError(e)) {
        return managementIdentityTransformer.toMigrationStatusUpdateRequest(tenant, e);
      }
    }
    return managementIdentityTransformer.toMigrationStatusUpdateRequest(tenant, null);
  }
}
