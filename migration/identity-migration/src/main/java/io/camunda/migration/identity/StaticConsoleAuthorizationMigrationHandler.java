/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity;

import static io.camunda.migration.identity.config.saas.StaticEntities.CLIENT_PERMISSIONS;
import static io.camunda.migration.identity.config.saas.StaticEntities.ROLE_PERMISSIONS;

import io.camunda.migration.api.MigrationException;
import io.camunda.migration.identity.dto.NoopDTO;
import io.camunda.security.auth.Authentication;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.AuthorizationServices.CreateAuthorizationRequest;
import java.util.List;

public class StaticConsoleAuthorizationMigrationHandler extends MigrationHandler<NoopDTO> {

  final AuthorizationServices authorizationServices;

  public StaticConsoleAuthorizationMigrationHandler(
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
    createClientAuthorizations();
  }

  private void createRoleAuthorizations() {
    createAuthorizations(ROLE_PERMISSIONS, "role");
  }

  private void createClientAuthorizations() {
    createAuthorizations(CLIENT_PERMISSIONS, "client");
  }

  private void createAuthorizations(
      final List<CreateAuthorizationRequest> requests, final String entity) {
    requests.forEach(
        request -> {
          try {
            authorizationServices.createAuthorization(request).join();
            logger.debug(
                "Migrated {} permission with owner ID: {} and resource type: {}",
                entity,
                request.ownerId(),
                request.resourceType());
          } catch (final Exception e) {
            if (!isConflictError(e)) {
              throw new MigrationException(
                  String.format(
                      "Failed to migrate %s permission with owner ID: %s and resource type: %s  ",
                      entity, request.ownerId(), request.resourceType()),
                  e);
            }
          }
        });
  }
}
