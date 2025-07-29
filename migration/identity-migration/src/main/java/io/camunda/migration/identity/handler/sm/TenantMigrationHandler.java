/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.handler.sm;

import static io.camunda.migration.identity.MigrationUtil.normalizeGroupID;
import static io.camunda.migration.identity.MigrationUtil.normalizeID;

import io.camunda.identity.sdk.users.dto.User;
import io.camunda.migration.api.MigrationException;
import io.camunda.migration.identity.client.ManagementIdentityClient;
import io.camunda.migration.identity.config.IdentityMigrationProperties;
import io.camunda.migration.identity.config.IdentityMigrationProperties.Mode;
import io.camunda.migration.identity.dto.Client;
import io.camunda.migration.identity.dto.Group;
import io.camunda.migration.identity.dto.Tenant;
import io.camunda.migration.identity.handler.MigrationHandler;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.TenantServices;
import io.camunda.service.TenantServices.TenantMemberRequest;
import io.camunda.service.TenantServices.TenantRequest;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class TenantMigrationHandler extends MigrationHandler<Tenant> {

  private static final String DEFAULT_TENANT_IDENTIFIER = "<default>";

  private final ManagementIdentityClient managementIdentityClient;
  private final TenantServices tenantService;
  private final Mode mode;

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
      final CamundaAuthentication camundaAuthentication,
      final IdentityMigrationProperties migrationProperties) {
    super(migrationProperties.getBackpressureDelay());
    this.managementIdentityClient = managementIdentityClient;
    this.tenantService = tenantService.withAuthentication(camundaAuthentication);
    mode = migrationProperties.getMode();
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
          var tenantId = tenant.tenantId();
          if (!tenant.tenantId().equals(DEFAULT_TENANT_IDENTIFIER)) {
            tenantId = normalizeID(tenant.tenantId());
            try {
              final var tenantDto = new TenantRequest(null, tenantId, tenant.name(), null);
              retryOnBackpressure(
                  () -> tenantService.createTenant(tenantDto).join(),
                  "Failed to create tenant with ID: " + tenantId);
              createdTenantCount.incrementAndGet();
              logger.debug(
                  "Tenant with ID '{}' and name '{}' created successfully.",
                  tenantId,
                  tenant.name());
            } catch (final Exception e) {
              if (!isConflictError(e)) {
                throw new MigrationException(
                    "Failed to migrate tenant with ID: " + tenant.tenantId(), e);
              }
              logger.debug(
                  "Tenant with ID '{}' already exists, skipping creation.", tenant.tenantId());
            }
          }
          if (Mode.OIDC.equals(mode)) {
            // In OIDC mode, we do not assign users, groups, or clients to the tenants.
            return;
          }
          assignUsersToTenant(tenant.tenantId(), tenantId);
          assignGroupsToTenant(tenant.tenantId(), tenantId);
          assignClientsToTenant(tenant.tenantId(), tenantId);
        });
  }

  @Override
  protected void logSummary() {
    if (Mode.OIDC.equals(mode)) {
      logger.info(
          "Tenant migration completed: Created {} out of {} tenants, the remaining existed already.",
          createdTenantCount.get(),
          totalTenantCount.get());
      return;
    }
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
            final var tenantMember =
                new TenantMemberRequest(normalizedTenantId, user.getUsername(), EntityType.USER);
            retryOnBackpressure(
                () -> tenantService.addMember(tenantMember).join(),
                "Failed to assign user with ID: " + user.getUsername() + " to tenant: " + tenantId);
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

  private void assignGroupsToTenant(final String tenantId, final String normalizedTenantId) {
    final List<Group> tenantGroups;
    try {
      tenantGroups = managementIdentityClient.fetchTenantGroups(tenantId);
    } catch (final Exception e) {
      if (!isNotImplementedError(e)) {
        throw new MigrationException("Failed to fetch groups for tenant with ID: " + tenantId, e);
      }
      logger.warn("Group tenant endpoint is not available, skipping group assignment.");
      return;
    }
    totalGroupAssignmentAttempts.set(tenantGroups.size());

    tenantGroups.forEach(
        group -> {
          try {
            final var normalizedGroupId = normalizeGroupID(group);
            final var tenantMember =
                new TenantMemberRequest(normalizedTenantId, normalizedGroupId, EntityType.GROUP);
            retryOnBackpressure(
                () -> tenantService.addMember(tenantMember).join(),
                "Failed to assign group with name: " + group.name() + " to tenant: " + tenantId);
            assignedGroupCount.incrementAndGet();
            logger.debug(
                "Group with name '{}' assigned to tenant '{}'.",
                normalizedGroupId,
                normalizedTenantId);
          } catch (final Exception e) {
            if (!isConflictError(e)) {
              throw new MigrationException(
                  "Failed to assign group with name: " + group.name() + " to tenant: " + tenantId,
                  e);
            }
            logger.debug(
                "Group with name '{}' already assigned to tenant '{}', skipping assignment.",
                group.name(),
                normalizedTenantId);
          }
        });
  }

  public void assignClientsToTenant(final String tenantId, final String normalizedTenantId) {
    final List<Client> tenantClients;
    try {
      tenantClients = managementIdentityClient.fetchTenantClients(tenantId);
    } catch (final Exception e) {
      if (!isNotImplementedError(e)) {
        throw new MigrationException("Failed to fetch clients for tenant with ID: " + tenantId, e);
      }
      logger.warn("Client tenant endpoint is not available, skipping client assignment.");
      return;
    }
    totalClientAssignmentAttempts.set(tenantClients.size());

    tenantClients.forEach(
        client -> {
          final var normalizedClientId = normalizeID(client.clientId());
          try {
            final var tenantMember =
                new TenantMemberRequest(normalizedTenantId, normalizedClientId, EntityType.CLIENT);
            retryOnBackpressure(
                () -> tenantService.addMember(tenantMember).join(),
                "Failed to assign client with ID: "
                    + normalizedClientId
                    + " to tenant: "
                    + tenantId);
            assignedClientCount.incrementAndGet();
            logger.debug(
                "Client with ID '{}' assigned to tenant '{}'.",
                normalizedClientId,
                normalizedTenantId);
          } catch (final Exception e) {
            if (!isConflictError(e)) {
              throw new MigrationException(
                  "Failed to assign client with ID: "
                      + normalizedClientId
                      + " to tenant: "
                      + tenantId,
                  e);
            }
            logger.debug(
                "Client with ID '{}' already assigned to tenant '{}', skipping assignment.",
                client.clientId(),
                normalizedTenantId);
          }
        });
  }
}
