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
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.Either;

public class PermissionsBehavior {

  public static final String OWNER_NOT_FOUND_MESSAGE =
      "Expected to find owner with key: '%d', but none was found";

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
}
