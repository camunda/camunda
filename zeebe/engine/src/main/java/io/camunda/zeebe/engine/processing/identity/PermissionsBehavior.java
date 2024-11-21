/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity;

import static io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior.UNAUTHORIZED_ERROR_MESSAGE;

import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior.AuthorizationRequest;
import io.camunda.zeebe.engine.state.immutable.AuthorizationState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.value.AuthorizationRecordValue.PermissionValue;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.Either;
import java.util.HashSet;

public class PermissionsBehavior {

  public static final String OWNER_NOT_FOUND_MESSAGE =
      "Expected to find owner with key: '%d', but none was found";
  public static final String PERMISSION_ALREADY_EXISTS_MESSAGE =
      "Expected to add '%s' permission for resource '%s' and resource identifiers '%s' for owner '%s', but this permission for resource identifiers '%s' already exist. Existing resource ids are: '%s'";
  public static final String PERMISSION_NOT_FOUND_MESSAGE =
      "Expected to remove '%s' permission for resource '%s' and resource identifiers '%s' for owner '%s', but this permission for resource identifiers '%s' is not found. Existing resource ids are: '%s'";

  private final AuthorizationState authorizationState;
  private final AuthorizationCheckBehavior authCheckBehavior;

  public PermissionsBehavior(
      final ProcessingState processingState, final AuthorizationCheckBehavior authCheckBehavior) {
    authorizationState = processingState.getAuthorizationState();
    this.authCheckBehavior = authCheckBehavior;
  }

  public Either<Rejection, AuthorizationRecord> isAuthorized(
      final TypedRecord<AuthorizationRecord> command) {
    final var authorizationRequest =
        new AuthorizationRequest(
            command, AuthorizationResourceType.AUTHORIZATION, PermissionType.UPDATE);

    if (!authCheckBehavior.isAuthorized(authorizationRequest)) {
      final var errorMessage =
          UNAUTHORIZED_ERROR_MESSAGE.formatted(
              authorizationRequest.getPermissionType(), authorizationRequest.getResourceType());
      return Either.left(new Rejection(RejectionType.UNAUTHORIZED, errorMessage));
    }

    return Either.right(command.getValue());
  }

  public Either<Rejection, AuthorizationRecord> ownerExists(
      final AuthorizationRecord authorizationRecord) {
    final var ownerKey = authorizationRecord.getOwnerKey();

    return authorizationState
        .getOwnerType(ownerKey)
        .map(
            ownerType -> {
              authorizationRecord.setOwnerType(ownerType);
              return Either.<Rejection, AuthorizationRecord>right(authorizationRecord);
            })
        .orElseGet(
            () ->
                Either.left(
                    new Rejection(
                        RejectionType.NOT_FOUND, OWNER_NOT_FOUND_MESSAGE.formatted(ownerKey))));
  }

  public Either<Rejection, AuthorizationRecord> permissionAlreadyExists(
      final AuthorizationRecord record) {
    for (final PermissionValue permission : record.getPermissions()) {
      final var addedResourceIds = permission.getResourceIds();
      final var currentResourceIds =
          authCheckBehavior.getAuthorizedResourceIdentifiers(
              record.getOwnerKey(),
              record.getOwnerType(),
              record.getResourceType(),
              permission.getPermissionType());

      final var duplicates = new HashSet<>(currentResourceIds);
      duplicates.retainAll(addedResourceIds);
      if (!duplicates.isEmpty()) {
        return Either.left(
            new Rejection(
                RejectionType.ALREADY_EXISTS,
                PERMISSION_ALREADY_EXISTS_MESSAGE.formatted(
                    permission.getPermissionType(),
                    record.getResourceType(),
                    addedResourceIds,
                    record.getOwnerKey(),
                    duplicates,
                    currentResourceIds)));
      }
    }

    return Either.right(record);
  }

  public Either<Rejection, AuthorizationRecord> permissionDoesNotExist(
      final AuthorizationRecord record) {
    for (final PermissionValue permission : record.getPermissions()) {
      final var currentResourceIdentifiers =
          authCheckBehavior.getAuthorizedResourceIdentifiers(
              record.getOwnerKey(),
              record.getOwnerType(),
              record.getResourceType(),
              permission.getPermissionType());

      final var removedResourceIds = permission.getResourceIds();
      if (!currentResourceIdentifiers.containsAll(removedResourceIds)) {
        final var differences = new HashSet<>(removedResourceIds);
        differences.removeAll(currentResourceIdentifiers);

        return Either.left(
            new Rejection(
                RejectionType.NOT_FOUND,
                PERMISSION_NOT_FOUND_MESSAGE.formatted(
                    permission.getPermissionType(),
                    record.getResourceType(),
                    removedResourceIds,
                    record.getOwnerKey(),
                    differences,
                    currentResourceIdentifiers)));
      }
    }

    return Either.right(record);
  }
}
