/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity;

import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior.AuthorizationRequest;
import io.camunda.zeebe.engine.state.authorization.PersistedAuthorization;
import io.camunda.zeebe.engine.state.immutable.AuthorizationState;
import io.camunda.zeebe.engine.state.immutable.MappingRuleState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.AuthorizationScope;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.Either;
import java.util.Set;

public class PermissionsBehavior {

  public static final String PERMISSIONS_ALREADY_EXISTS_MESSAGE =
      "Expected to create authorization for owner '%s' for resource identifier '%s', but an authorization for this resource identifier already exists.";
  public static final String AUTHORIZATION_DOES_NOT_EXIST_ERROR_MESSAGE_UPDATE =
      "Expected to update authorization with key %s, but an authorization with this key does not exist";
  public static final String AUTHORIZATION_DOES_NOT_EXIST_ERROR_MESSAGE_DELETION =
      "Expected to delete authorization with key %s, but an authorization with this key does not exist";
  public static final String MAPPING_RULE_DOES_NOT_EXIST_ERROR_MESSAGE =
      "Expected to create or update authorization with ownerId '%s', but a mapping rule with this ID does not exist.";

  private final AuthorizationState authorizationState;
  private final AuthorizationCheckBehavior authCheckBehavior;
  private final MappingRuleState mappingRuleState;

  public PermissionsBehavior(
      final ProcessingState processingState, final AuthorizationCheckBehavior authCheckBehavior) {
    authorizationState = processingState.getAuthorizationState();
    mappingRuleState = processingState.getMappingRuleState();
    this.authCheckBehavior = authCheckBehavior;
  }

  public Either<Rejection, AuthorizationRecord> isAuthorized(
      final TypedRecord<AuthorizationRecord> command) {
    return isAuthorized(command, PermissionType.UPDATE);
  }

  public Either<Rejection, AuthorizationRecord> isAuthorized(
      final TypedRecord<AuthorizationRecord> command, final PermissionType permissionType) {
    final var authorizationRequest =
        new AuthorizationRequest(command, AuthorizationResourceType.AUTHORIZATION, permissionType);
    return authCheckBehavior.isAuthorized(authorizationRequest).map(unused -> command.getValue());
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
      final var addedAuthorizationScope = AuthorizationScope.of(record.getResourceId());
      final var currentAuthorizationScopes =
          authCheckBehavior.getDirectAuthorizedAuthorizationScopes(
              record.getOwnerType(), record.getOwnerId(), record.getResourceType(), permission);

      if (currentAuthorizationScopes.contains(addedAuthorizationScope)) {
        return Either.left(
            new Rejection(
                RejectionType.ALREADY_EXISTS,
                PERMISSIONS_ALREADY_EXISTS_MESSAGE.formatted(
                    record.getOwnerId(), addedAuthorizationScope)));
      }
    }
    return Either.right(record);
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

  public Either<Rejection, AuthorizationRecord> mappingRuleExists(
      final AuthorizationRecord record) {
    if (record.getOwnerType() != AuthorizationOwnerType.MAPPING_RULE) {
      return Either.right(record);
    }

    if (mappingRuleState.get(record.getOwnerId()).isEmpty()) {
      return Either.left(
          new Rejection(
              RejectionType.NOT_FOUND,
              MAPPING_RULE_DOES_NOT_EXIST_ERROR_MESSAGE.formatted(record.getOwnerId())));
    }

    return Either.right(record);
  }
}
