/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity;

import static io.camunda.migration.identity.config.saas.StaticEntities.ROLE_PERMISSIONS;

import io.camunda.migration.api.MigrationException;
import io.camunda.migration.identity.dto.NoopDTO;
import io.camunda.security.auth.Authentication;
import io.camunda.service.AuthorizationServices;
import java.util.List;

public class RoleAuthorizationMigrationHandler extends MigrationHandler<NoopDTO> {

  final AuthorizationServices authorizationServices;

  public RoleAuthorizationMigrationHandler(
      final AuthorizationServices authorizationServices,
      final Authentication servicesAuthentication) {
    this.authorizationServices = authorizationServices.withAuthentication(servicesAuthentication);
  }

  @Override
  protected List<NoopDTO> fetchBatch(final int page) {
    // Permissions for roles are created statically.
    return List.of();
  }

  @Override
  protected void process(final List<NoopDTO> batch) {
    createRoleAuthorizations();
  }

  private void createRoleAuthorizations() {
    ROLE_PERMISSIONS.forEach(
        request -> {
          try {
            authorizationServices.createAuthorization(request).join();
          } catch (final Exception e) {
            if (!isConflictError(e)) {
              throw new MigrationException(
                  String.format(
                      "Failed to migrate role permission with owner ID: %s and resource type: %s  ",
                      request.ownerId(), request.resourceType()),
                  e);
            }
          }
        });
  }
}
