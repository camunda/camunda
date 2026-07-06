/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.resource;

import io.camunda.zeebe.engine.processing.identity.authorization.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.authorization.exception.ForbiddenException;
import io.camunda.zeebe.engine.processing.identity.authorization.request.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.resource.ResourceDeletionExceptions.NoSuchResourceException;
import io.camunda.zeebe.protocol.impl.record.value.resource.ResourceDeletionRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.ResourceType;
import io.camunda.zeebe.stream.api.records.TypedRecord;

public final class ResourceDeletionAuthorizationBehavior {

  private final AuthorizationCheckBehavior authCheckBehavior;

  ResourceDeletionAuthorizationBehavior(final AuthorizationCheckBehavior authCheckBehavior) {
    this.authCheckBehavior = authCheckBehavior;
  }

  void authorize(
      final TypedRecord<ResourceDeletionRecord> command,
      final PermissionType permissionType,
      final String resourceId,
      final String tenantId) {
    final var authRequest =
        AuthorizationRequest.builder()
            .command(command)
            .resourceType(AuthorizationResourceType.RESOURCE)
            .permissionType(permissionType)
            .tenantId(tenantId)
            .addResourceId(resourceId)
            .build();
    if (authCheckBehavior.isAuthorizedOrInternalCommand(authRequest).isLeft()) {
      throw new ForbiddenException(authRequest);
    }
  }

  /**
   * Checks authorization for history-only deletion (no resource found, only history remains).
   * Distinguishes NOT_FOUND from FORBIDDEN so the caller can throw the appropriate exception.
   */
  void checkAuthorizationForHistoryDeletion(final TypedRecord<ResourceDeletionRecord> command) {
    final var commandValue = command.getValue();
    final var resourceType = commandValue.getResourceType();
    final var authRequest =
        AuthorizationRequest.builder()
            .command(command)
            .resourceType(AuthorizationResourceType.RESOURCE)
            .permissionType(
                resourceType == ResourceType.PROCESS_DEFINITION
                    ? PermissionType.DELETE_PROCESS
                    : PermissionType.DELETE_DRD)
            .addResourceId(commandValue.getResourceId())
            .tenantId(commandValue.getTenantId())
            .build();
    final var authResponse = authCheckBehavior.isAuthorizedOrInternalCommand(authRequest);
    if (authResponse.isLeft()) {
      if (authResponse.getLeft().type() == RejectionType.NOT_FOUND) {
        throw new NoSuchResourceException(commandValue.getResourceKey());
      } else {
        throw new ForbiddenException(authRequest);
      }
    }
  }
}
