/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity;

import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.identity.authorization.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.authorization.request.AuthorizationRequest;
import io.camunda.zeebe.engine.state.authorization.PersistedAuthorization;
import io.camunda.zeebe.engine.state.immutable.AuthorizationState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.AuthorizationScope;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.Either;
import java.util.Set;

public class PermissionsBehavior {

  public static final String PERMISSIONS_FOR_RESOURCE_IDENTIFIER_ALREADY_EXISTS_MESSAGE =
      "Expected to create authorization for owner '%s' for resource identifier '%s', but an authorization for this resource identifier already exists.";
  public static final String PERMISSIONS_FOR_RESOURCE_PROPERTY_NAME_ALREADY_EXISTS_MESSAGE =
      "Expected to create authorization for owner '%s' for resource property name '%s', but an authorization for this resource property name already exists.";
  public static final String AUTHORIZATION_DOES_NOT_EXIST_ERROR_MESSAGE_UPDATE =
      "Expected to update authorization with key %s, but an authorization with this key does not exist";
  public static final String AUTHORIZATION_DOES_NOT_EXIST_ERROR_MESSAGE_DELETION =
      "Expected to delete authorization with key %s, but an authorization with this key does not exist";

  private final AuthorizationState authorizationState;
  private final AuthorizationCheckBehavior authCheckBehavior;

  public PermissionsBehavior(
      final ProcessingState processingState, final AuthorizationCheckBehavior authCheckBehavior) {
    authorizationState = processingState.getAuthorizationState();
    this.authCheckBehavior = authCheckBehavior;
  }

  public Either<Rejection, AuthorizationRecord> isAuthorized(
      final TypedRecord<AuthorizationRecord> command) {
    return isAuthorized(command, PermissionType.UPDATE);
  }

  public Either<Rejection, AuthorizationRecord> isAuthorized(
      final TypedRecord<AuthorizationRecord> command, final PermissionType permissionType) {
    final var authorizationRequest =
        AuthorizationRequest.builder()
            .command(command)
            .resourceType(AuthorizationResourceType.AUTHORIZATION)
            .permissionType(permissionType)
            .build();
    return authCheckBehavior
        .isAuthorizedOrInternalCommand(authorizationRequest)
        .map(unused -> command.getValue());
  }

  public Either<Rejection, PersistedAuthorization> authorizationExists(
      final AuthorizationRecord authorizationRecord, final String rejectionMessage) {
    final var key = authorizationRecord.getAuthorizationKey();
    return authorizationState
        .get(key)
        .map(Either::<Rejection, PersistedAuthorization>right)
        .orElseGet(
            () ->
                Either.left(
                    new Rejection(RejectionType.NOT_FOUND, rejectionMessage.formatted(key))));
  }

  public Either<Rejection, AuthorizationRecord> permissionsAlreadyExist(
      final AuthorizationRecord record) {
    for (final PermissionType permission : record.getPermissionTypes()) {
      final var addedAuthorizationScope = createAuthorizationScope(record);
      final var currentAuthorizationScopes =
          authCheckBehavior.getDirectAuthorizedAuthorizationScopes(
              record.getOwnerType(), record.getOwnerId(), record.getResourceType(), permission);

      if (currentAuthorizationScopes.contains(addedAuthorizationScope)) {
        final var rejectionReason = createDuplicatePermissionRejectionReason(record);
        return Either.left(new Rejection(RejectionType.ALREADY_EXISTS, rejectionReason));
      }
    }
    return Either.right(record);
  }

  private AuthorizationScope createAuthorizationScope(final AuthorizationRecord record) {
    return new AuthorizationScope(
        record.getResourceMatcher(), record.getResourceId(), record.getResourcePropertyName());
  }

  private String createDuplicatePermissionRejectionReason(final AuthorizationRecord record) {
    final var ownerId = record.getOwnerId();
    return record.getResourceMatcher() == AuthorizationResourceMatcher.PROPERTY
        ? PERMISSIONS_FOR_RESOURCE_PROPERTY_NAME_ALREADY_EXISTS_MESSAGE.formatted(
            ownerId, record.getResourcePropertyName())
        : PERMISSIONS_FOR_RESOURCE_IDENTIFIER_ALREADY_EXISTS_MESSAGE.formatted(
            ownerId, record.getResourceId());
  }

  public Either<Rejection, AuthorizationRecord> hasValidPermissionTypes(
      final AuthorizationRecord record,
      final Set<PermissionType> permissionTypes,
      final AuthorizationResourceType resourceType,
      final String rejectionMessage) {
    if (resourceType.getSupportedPermissionTypes().containsAll(record.getPermissionTypes())) {
      return Either.right(record);
    }

    permissionTypes.removeAll(resourceType.getSupportedPermissionTypes());

    return Either.left(
        new Rejection(
            RejectionType.INVALID_ARGUMENT,
            rejectionMessage.formatted(
                permissionTypes, resourceType, resourceType.getSupportedPermissionTypes())));
  }
}
