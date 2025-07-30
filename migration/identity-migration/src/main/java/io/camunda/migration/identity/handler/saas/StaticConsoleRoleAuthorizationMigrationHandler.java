/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.handler.saas;

import static io.camunda.migration.identity.config.saas.StaticEntities.ROLE_PERMISSIONS;

import io.camunda.migration.api.MigrationException;
import io.camunda.migration.identity.config.IdentityMigrationProperties;
import io.camunda.migration.identity.dto.NoopDTO;
import io.camunda.migration.identity.handler.MigrationHandler;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.AuthorizationServices;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class StaticConsoleRoleAuthorizationMigrationHandler extends MigrationHandler<NoopDTO> {

  final AuthorizationServices authorizationServices;

  private final AtomicInteger totalRolePermissions = new AtomicInteger();
  private final AtomicInteger createdAuthorizations = new AtomicInteger();
  private final AtomicInteger skippedAuthorizations = new AtomicInteger();

  public StaticConsoleRoleAuthorizationMigrationHandler(
      final AuthorizationServices authorizationServices,
      final CamundaAuthentication servicesAuthentication,
      final IdentityMigrationProperties migrationProperties) {
    super(migrationProperties.getBackpressureDelay());
    this.authorizationServices = authorizationServices.withAuthentication(servicesAuthentication);
  }

  @Override
  protected List<NoopDTO> fetchBatch(final int page) {
    // Permissions for roles are created statically.
    return List.of();
  }

  @Override
  protected void process(final List<NoopDTO> batch) {
    totalRolePermissions.set(ROLE_PERMISSIONS.size());

    ROLE_PERMISSIONS.forEach(
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
