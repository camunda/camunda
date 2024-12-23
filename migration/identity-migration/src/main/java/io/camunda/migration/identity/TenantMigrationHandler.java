/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity;

import static io.camunda.migration.identity.midentity.ManagementIdentityTransformer.toMigrationStatusUpdateRequest;

import io.camunda.migration.identity.dto.MigrationStatusUpdateRequest;
import io.camunda.migration.identity.dto.Tenant;
import io.camunda.migration.identity.midentity.ManagementIdentityClient;
import io.camunda.security.auth.Authentication;
import io.camunda.service.TenantServices;
import io.camunda.service.TenantServices.TenantDTO;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class TenantMigrationHandler implements MigrationHandler {

  private static final Logger LOG = LoggerFactory.getLogger(TenantMigrationHandler.class);
  private final ManagementIdentityClient managementIdentityClient;
  private final TenantServices tenantServices;

  public TenantMigrationHandler(
      final Authentication authentication,
      final ManagementIdentityClient managementIdentityClient,
      final TenantServices tenantServices) {
    this.managementIdentityClient = managementIdentityClient;
    this.tenantServices = tenantServices.withAuthentication(authentication);
  }

  @Override
  public void migrate() {
    LOG.debug("Migrating tenants");
    List<Tenant> tenants;
    do {
      tenants = managementIdentityClient.fetchTenants(SIZE);
      managementIdentityClient.updateMigrationStatus(
          tenants.stream().map(this::createTenant).toList());
    } while (!tenants.isEmpty());
    LOG.debug("Finished migrating tenants");
  }

  private MigrationStatusUpdateRequest createTenant(final Tenant tenant) {
    try {
      tenantServices.createTenant(new TenantDTO(null, tenant.tenantId(), tenant.name())).join();
    } catch (final Exception e) {
      if (!isConflictError(e)) {
        return toMigrationStatusUpdateRequest(tenant, e);
      }
    }
    return toMigrationStatusUpdateRequest(tenant, null);
  }
}
