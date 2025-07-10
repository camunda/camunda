/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.handler.sm;

import static io.camunda.migration.identity.MigrationUtil.normalizeID;

import io.camunda.identity.sdk.users.dto.User;
import io.camunda.migration.api.MigrationException;
import io.camunda.migration.identity.client.ManagementIdentityClient;
import io.camunda.migration.identity.dto.Tenant;
import io.camunda.migration.identity.handler.MigrationHandler;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.TenantServices;
import io.camunda.service.TenantServices.TenantDTO;
import io.camunda.service.TenantServices.TenantMemberRequest;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class TenantMigrationHandler extends MigrationHandler<Tenant> {

  private final ManagementIdentityClient managementIdentityClient;
  private final TenantServices tenantService;

  private final AtomicInteger createdTenantCount = new AtomicInteger();
  private final AtomicInteger totalTenantCount = new AtomicInteger();
  private final AtomicInteger assignedUserCount = new AtomicInteger();
  private final AtomicInteger assignedGroupCount = new AtomicInteger();
  private final AtomicInteger assignedClientCount = new AtomicInteger();
  private final AtomicInteger totalUserAssignmentAttempts = new AtomicInteger();
  private final AtomicInteger totalGroupAssignmentAttempts = new AtomicInteger();
  private final AtomicInteger totalClientAssignmentAttempts = new AtomicInteger();

  public TenantMigrationHandler(
      final ManagementIdentityClient managementIdentityClient,
      final TenantServices tenantService,
      final CamundaAuthentication camundaAuthentication) {
    this.managementIdentityClient = managementIdentityClient;
    this.tenantService = tenantService.withAuthentication(camundaAuthentication);
  }

  @Override
  protected List<Tenant> fetchBatch(final int page) {
    return List.of();
  }

  @Override
  protected void process(final List<Tenant> batch) {
    final List<Tenant> tenants;
    try {
      tenants = managementIdentityClient.fetchTenants();
    } catch (final Exception e) {
      if (!isNotImplementedError(e)) {
        throw new MigrationException("Failed to fetch tenants", e);
      }
      logger.warn("Tenant endpoint is not available, skipping.");
      return;
    }
    totalTenantCount.set(tenants.size());

    tenants.forEach(
        tenant -> {
          final var tenantId = normalizeID(tenant.tenantId());
          try {
            tenantService.createTenant(new TenantDTO(null, tenantId, tenant.name(), null)).join();
            createdTenantCount.incrementAndGet();
            logger.debug(
                "Tenant with ID '{}' and name '{}' created successfully.", tenantId, tenant.name());
          } catch (final Exception e) {
            if (!isConflictError(e)) {
              throw new MigrationException(
                  "Failed to migrate tenant with ID: " + tenant.tenantId(), e);
            }
            logger.debug(
                "Tenant with ID '{}' already exists, skipping creation.", tenant.tenantId());
          }
          assignUsersToTenant(tenant.tenantId(), tenantId);
        });
  }

  @Override
  protected void logSummary() {
    logger.info(
        "Tenant migration completed: Created {} out of {} tenants, the remaining existed already. Assigned {} users out of {} attempted, the remaining were already assigned. Assigned {} groups out of {} attempted, the remaining were already assigned. Assigned {} clients out of {} attempted, the remaining were already assigned.",
        createdTenantCount.get(),
        totalTenantCount.get(),
        assignedUserCount.get(),
        totalUserAssignmentAttempts.get(),
        assignedGroupCount.get(),
        totalGroupAssignmentAttempts.get(),
        assignedClientCount.get(),
        totalClientAssignmentAttempts.get());
  }

  private void assignUsersToTenant(final String tenantId, final String normalizedTenantId) {
    final List<User> tenantUsers;
    try {
      tenantUsers = managementIdentityClient.fetchTenantUsers(tenantId);
    } catch (final Exception e) {
      if (!isNotImplementedError(e)) {
        throw new MigrationException("Failed to fetch users for tenant with ID: " + tenantId, e);
      }
      logger.warn("User tenant endpoint is not available, skipping user assignment.");
      return;
    }
    totalUserAssignmentAttempts.set(tenantUsers.size());

    tenantUsers.forEach(
        user -> {
          try {
            tenantService
                .addMember(
                    new TenantMemberRequest(
                        normalizedTenantId, user.getUsername(), EntityType.USER))
                .join();
            assignedUserCount.incrementAndGet();
            logger.debug(
                "User with username '{}' assigned to tenant '{}'.",
                user.getUsername(),
                normalizedTenantId);
          } catch (final Exception e) {
            if (!isConflictError(e)) {
              throw new MigrationException(
                  "Failed to assign user with ID: "
                      + user.getUsername()
                      + " to tenant: "
                      + tenantId,
                  e);
            }
            logger.debug(
                "User with ID '{}' already assigned to tenant '{}', skipping assignment.",
                user.getUsername(),
                normalizedTenantId);
          }
        });
  }
}
