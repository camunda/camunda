/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.handler.saas;

import static io.camunda.migration.identity.config.ResourceBasedAuthorizationConstants.RBA_IRRELEVANT_RESOURCE_TYPES;
import static io.camunda.migration.identity.config.saas.StaticEntities.ROLE_PERMISSIONS;
import static io.camunda.migration.identity.config.saas.StaticEntities.TASK_USER_ROLE_ID;

import io.camunda.migration.api.MigrationException;
import io.camunda.migration.identity.config.IdentityMigrationProperties;
import io.camunda.migration.identity.dto.NoopDTO;
import io.camunda.migration.identity.handler.MigrationHandler;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.AuthorizationServices.CreateAuthorizationRequest;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class StaticConsoleRoleAuthorizationMigrationHandler extends MigrationHandler<NoopDTO> {

  final AuthorizationServices authorizationServices;

  private final AtomicInteger totalRolePermissions = new AtomicInteger();
  private final AtomicInteger createdAuthorizations = new AtomicInteger();
  private final AtomicInteger skippedAuthorizations = new AtomicInteger();
  private boolean resourceBasedAuthorizationsEnabled = false;

  public StaticConsoleRoleAuthorizationMigrationHandler(
      final AuthorizationServices authorizationServices,
      final CamundaAuthentication servicesAuthentication,
      final IdentityMigrationProperties migrationProperties) {
    super(migrationProperties.getBackpressureDelay());
    this.authorizationServices = authorizationServices.withAuthentication(servicesAuthentication);
    resourceBasedAuthorizationsEnabled = migrationProperties.isResourceAuthorizationsEnabled();
  }

  @Override
  protected List<NoopDTO> fetchBatch(final int page) {
    // Permissions for roles are created statically.
    return List.of();
  }

  @Override
  protected void process(final List<NoopDTO> batch) {
    List<CreateAuthorizationRequest> rolePermissionsToCreate = ROLE_PERMISSIONS;

    if (resourceBasedAuthorizationsEnabled) {
      rolePermissionsToCreate =
          ROLE_PERMISSIONS.stream()
              .filter(auth -> RBA_IRRELEVANT_RESOURCE_TYPES.contains(auth.resourceType()))
              .collect(Collectors.toList());

      // for the Tasklist role there were no RBA restrictions on task access, thus adding task
      // permissions still
      rolePermissionsToCreate.add(
          new CreateAuthorizationRequest(
              TASK_USER_ROLE_ID,
              AuthorizationOwnerType.ROLE,
              "*",
              AuthorizationResourceType.PROCESS_DEFINITION,
              Set.of(PermissionType.READ_USER_TASK, PermissionType.UPDATE_USER_TASK)));
    }

    totalRolePermissions.set(rolePermissionsToCreate.size());

    rolePermissionsToCreate.forEach(
        request -> {
          try {
            retryOnBackpressure(
                () -> authorizationServices.createAuthorization(request).join(),
                "migrating role permission with owner ID: "
                    + request.ownerId()
                    + " and resource type: "
                    + request.resourceType());
            createdAuthorizations.incrementAndGet();
            logger.debug(
                "Migrated role permission with owner ID: {} and resource type: {}",
                request.ownerId(),
                request.resourceType());
          } catch (final Exception e) {
            if (!isConflictError(e)) {
              throw new MigrationException(
                  String.format(
                      "Failed to migrate role permission with owner ID: %s and resource type: %s  ",
                      request.ownerId(), request.resourceType()),
                  e);
            }
            skippedAuthorizations.incrementAndGet();
            logger.debug(
                "Role permission for owner ID: {} and resource type: {} already exists, skipping.",
                request.ownerId(),
                request.resourceType());
          }
        });
  }

  @Override
  protected void logSummary() {
    logger.info(
        "Role authorization migration complete: Created {} authorizations out of {} total. Skipped {} as they already exist.",
        createdAuthorizations.get(),
        totalRolePermissions.get(),
        skippedAuthorizations.get());
  }
}
