/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity;

import io.camunda.migration.identity.dto.Tenant;
import io.camunda.zeebe.client.ZeebeClient;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TenantMigrationHandler implements MigrationHandler {
  private final ManagementIdentityProxy managementIdentityProxy;
  private final ZeebeClient client;

  public TenantMigrationHandler(
      final ManagementIdentityProxy managementIdentityProxy, final ZeebeClient client) {
    this.managementIdentityProxy = managementIdentityProxy;
    this.client = client;
  }

  @Override
  public void migrate() {
    Tenant lastTenant = null;
    do {
      final List<Tenant> tenants = managementIdentityProxy.fetchTenants(lastTenant, 100);
      // if error code is duplicate ignore
      tenants.forEach(this::createTenant);
      managementIdentityProxy.markTenantsAsMigrated(tenants);
      lastTenant = tenants.isEmpty() ? null : tenants.get(tenants.size() - 1);
    } while (lastTenant != null);
  }

  private void createTenant(final Tenant tenant) {
    try {
      client.newCreateTenantCommand().name(tenant.name()).tenantId(tenant.tenantId()).send().join();
    } catch (final Exception e) {
      if (!isConflictError(e)) {
        throw e;
      }
    }
  }

  private boolean isConflictError(final Exception e) {
    return e.getMessage().contains("Failed with code 409: 'Conflict'");
  }
}
