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
import io.camunda.search.entities.MappingEntity;
import io.camunda.service.MappingServices;
import io.camunda.service.MappingServices.MappingDTO;
import io.camunda.service.TenantServices;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class UserTenantsMigrationHandler extends MigrationHandler<UserTenants> {
  private static final String USERNAME_CLAIM = "sub";
  private final ManagementIdentityClient managementIdentityClient;
  private final ManagementIdentityTransformer managementIdentityTransformer;
  private final TenantServices tenantServices;
  private final MappingServices mappingServices;

  public UserTenantsMigrationHandler(
      final ManagementIdentityClient managementIdentityClient,
      final ManagementIdentityTransformer managementIdentityTransformer,
      final TenantServices tenantServices,
      final MappingServices mappingServices) {
    this.managementIdentityClient = managementIdentityClient;
    this.managementIdentityTransformer = managementIdentityTransformer;
    this.tenantServices = tenantServices;
    this.mappingServices = mappingServices;
  }

  @Override
  protected List<UserTenants> fetchBatch() {
    return managementIdentityClient.fetchUserTenants(SIZE);
  }

  @Override
  protected void process(final List<UserTenants> batch) {
    managementIdentityClient.updateMigrationStatus(batch.stream().map(this::processTask).toList());
  }

  public MigrationStatusUpdateRequest processTask(final UserTenants userTenants) {
    try {
      final var mapping =
          new MappingDTO(
              USERNAME_CLAIM,
              userTenants.username(),
              userTenants.username() + "_mapping",
              userTenants.username() + "_mapping");

      final long mappingKey =
          mappingServices
              .findMapping(mapping)
              .map(MappingEntity::mappingKey)
              .orElseGet(() -> mappingServices.createMapping(mapping).join().getMappingKey());
      for (final Tenant userTenant : userTenants.tenants()) {
        final var tenant = tenantServices.getById(userTenant.tenantId());
        assignMemberToTenant(tenant.tenantId(), mappingKey);
      }
      return managementIdentityTransformer.toMigrationStatusUpdateRequest(userTenants, null);
    } catch (final Exception e) {
      return managementIdentityTransformer.toMigrationStatusUpdateRequest(userTenants, e);
    }
  }

  private void assignMemberToTenant(final String tenantId, final long mappingKey) {
    try {
      tenantServices.addMember(tenantId, EntityType.MAPPING, mappingKey).join();
    } catch (final Exception e) {
      if (!isConflictError(e)) {
        throw e;
      }
    }
  }
}
