/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity;

import static io.camunda.migration.identity.midentity.ManagementIdentityTransformer.toMigrationStatusUpdateRequest;

import io.camunda.migration.identity.dto.GroupTenants;
import io.camunda.migration.identity.dto.MigrationStatusUpdateRequest;
import io.camunda.migration.identity.midentity.ManagementIdentityClient;
import io.camunda.service.GroupServices;
import io.camunda.service.TenantServices;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
final class GroupTenantsMigrationHandler implements MigrationHandler {

  private static final Logger LOG = LoggerFactory.getLogger(GroupTenantsMigrationHandler.class);
  private final ManagementIdentityClient managementIdentityClient;
  private final GroupServices groupServices;
  private final TenantServices tenantServices;

  public GroupTenantsMigrationHandler(
      final ManagementIdentityClient managementIdentityClient,
      final GroupServices groupServices,
      final TenantServices tenantServices) {
    this.managementIdentityClient = managementIdentityClient;
    this.groupServices = groupServices;
    this.tenantServices = tenantServices;
  }

  @Override
  public void migrate() {
    LOG.debug("Starting");

    while (true) {
      final var groupTenantsPage = managementIdentityClient.fetchGroupTenants(SIZE);
      if (groupTenantsPage.isEmpty()) {
        break;
      }

      LOG.trace("Found {} groups with tenants", groupTenantsPage.size());
      final var updates = groupTenantsPage.stream().map(this::createGroupTenants).toList();
      managementIdentityClient.updateMigrationStatus(updates);
    }

    LOG.debug("Finished");
  }

  private MigrationStatusUpdateRequest createGroupTenants(final GroupTenants groupTenants) {
    final var groupName = groupTenants.name();
    final var group = groupServices.findGroup(groupName);
    if (group.isEmpty()) {
      LOG.warn("Group {} not found", groupName);
      return toMigrationStatusUpdateRequest(
          groupTenants, new NoSuchElementException("Group %s not found".formatted(groupName)));
    }
    final var groupKey = group.get().groupKey();
    for (final var tenant : groupTenants.tenants()) {
      final var tenantId = tenant.tenantId();
      LOG.trace("Assigning group {} to tenant {}", groupName, tenantId);
      try {
        final var tenantKey = tenantServices.getById(tenantId).key();
        tenantServices.addMember(tenantKey, EntityType.GROUP, groupKey);
      } catch (final Exception e) {
        if (!isConflictError(e)) {
          LOG.warn("Failed to assign group {} to tenant {}", groupName, tenantId, e);
          return toMigrationStatusUpdateRequest(groupTenants, e);
        }
        LOG.trace("Group {} is already assigned to tenant {}", groupName, tenantId);
      }
    }

    return toMigrationStatusUpdateRequest(groupTenants, null);
  }
}
