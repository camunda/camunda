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
import io.camunda.migration.identity.dto.UserTenants;
import io.camunda.migration.identity.midentity.ManagementIdentityClient;
import io.camunda.migration.identity.midentity.ManagementIdentityTransformer;
import io.camunda.migration.identity.service.MappingService;
import io.camunda.migration.identity.service.TenantService;
import io.camunda.search.entities.TenantEntity;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class UserTenantsMigrationHandler implements MigrationHandler {

  private final ManagementIdentityClient managementIdentityClient;
  private final ManagementIdentityTransformer managementIdentityTransformer;
  private final TenantService tenantService;
  private final MappingService mappingService;

  public UserTenantsMigrationHandler(
      final ManagementIdentityClient managementIdentityClient,
      final ManagementIdentityTransformer managementIdentityTransformer,
      final TenantService tenantService,
      final MappingService mappingService) {
    this.managementIdentityClient = managementIdentityClient;
    this.managementIdentityTransformer = managementIdentityTransformer;
    this.tenantService = tenantService;
    this.mappingService = mappingService;
  }

  @Override
  public void migrate() {
    List<UserTenants> userTenants;
    do {
      userTenants = managementIdentityClient.fetchUserTenants(SIZE);
      managementIdentityClient.updateMigrationStatus(
          userTenants.stream().map(this::createTenantUser).toList());
    } while (!userTenants.isEmpty());
  }

  private MigrationStatusUpdateRequest createTenantUser(final UserTenants userTenants) {

    try {
      final var userKey = mappingService.findOrCreateUserWithUsername(userTenants.username());

      for (final Tenant userTenant : userTenants.tenants()) {
        final var tenant = tenantService.fetch(userTenant.tenantId(), userTenant.name());
        assignMemberToTenant(tenant, userKey);
      }
      return managementIdentityTransformer.toMigrationStatusUpdateRequest(userTenants, null);
    } catch (final Exception e) {
      return managementIdentityTransformer.toMigrationStatusUpdateRequest(userTenants, e);
    }
  }

  private void assignMemberToTenant(final TenantEntity tenant, final Long userKey) {
    try {
      tenantService.assignMappingToTenant(tenant.key(), userKey);
    } catch (final Exception e) {
      if (!isConflictError(e)) {
        throw e;
      }
    }
  }
}
