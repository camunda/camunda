/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity;

import io.camunda.migration.identity.dto.MigrationStatusUpdateRequest;
import io.camunda.migration.identity.dto.Tenant;
import io.camunda.migration.identity.midentity.ManagementIdentityClient;
import io.camunda.migration.identity.midentity.ManagementIdentityTransformer;
import io.camunda.security.auth.Authentication;
import io.camunda.service.TenantServices;
import io.camunda.service.TenantServices.TenantDTO;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TenantMigrationHandler extends MigrationHandler<Tenant> {

  private final ManagementIdentityClient managementIdentityClient;
  private final ManagementIdentityTransformer managementIdentityTransformer;
  private final TenantServices tenantServices;

  public TenantMigrationHandler(
      final Authentication authentication,
      final ManagementIdentityClient managementIdentityClient,
      final ManagementIdentityTransformer managementIdentityTransformer,
      final TenantServices tenantServices) {
    this.managementIdentityClient = managementIdentityClient;
    this.managementIdentityTransformer = managementIdentityTransformer;
    this.tenantServices = tenantServices.withAuthentication(authentication);
  }

  @Override
  protected List<Tenant> fetchBatch() {
    return managementIdentityClient.fetchTenants(SIZE);
  }

  @Override
  protected void process(final List<Tenant> batch) {
    managementIdentityClient.updateMigrationStatus(batch.stream().map(this::processTask).toList());
  }

  protected MigrationStatusUpdateRequest processTask(final Tenant tenant) {
    try {
      tenantServices.createTenant(new TenantDTO(tenant.tenantId(), tenant.name(), null)).join();
    } catch (final Exception e) {
      if (!isConflictError(e)) {
        return managementIdentityTransformer.toMigrationStatusUpdateRequest(tenant, e);
      }
    }
    return managementIdentityTransformer.toMigrationStatusUpdateRequest(tenant, null);
  }
}
