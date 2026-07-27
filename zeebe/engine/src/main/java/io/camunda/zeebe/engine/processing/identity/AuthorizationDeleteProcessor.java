/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity;

import static io.camunda.zeebe.engine.processing.identity.PermissionsBehavior.AUTHORIZATION_DOES_NOT_EXIST_ERROR_MESSAGE_DELETION;

import io.camunda.security.api.model.authz.DefaultRole;
import io.camunda.zeebe.engine.Loggers;
import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.identity.adapter.AuthorizationScopeStateAdapter;
import io.camunda.zeebe.engine.processing.identity.authorization.CslAuthorizationCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.DistributedTypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.SideEffectWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.authorization.PersistedAuthorization;
import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.Either;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;

@NullMarked
public class AuthorizationDeleteProcessor
    implements DistributedTypedRecordProcessor<AuthorizationRecord> {

  public static final String AUTHORIZATION_OWNER_PROTECTED_ERROR_MESSAGE =
      "Expected to delete authorization with key %s, but it belongs to default role '%s' "
          + "whose authorizations cannot be deleted.";

  private static final Logger LOG = Loggers.ENGINE_IDENTITY_LOGGER;

  private final KeyGenerator keyGenerator;
  private final CommandDistributionBehavior distributionBehavior;
  private final StateWriter stateWriter;
  private final TypedResponseWriter responseWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final SideEffectWriter sideEffectWriter;
  private final PermissionsBehavior permissionsBehavior;
  private final AuthorizationScopeStateAdapter authorizationScopeStateAdapter;

  public AuthorizationDeleteProcessor(
      final Writers writers,
      final KeyGenerator keyGenerator,
      final MutableProcessingState processingState,
      final CommandDistributionBehavior distributionBehavior,
      final CslAuthorizationCheck cslCheck,
      final AuthorizationScopeStateAdapter authorizationScopeStateAdapter) {
    this.keyGenerator = keyGenerator;
    this.distributionBehavior = distributionBehavior;
    stateWriter = writers.state();
    responseWriter = writers.response();
    rejectionWriter = writers.rejection();
    sideEffectWriter = writers.sideEffect();
    permissionsBehavior = new PermissionsBehavior(processingState, cslCheck);
    this.authorizationScopeStateAdapter = authorizationScopeStateAdapter;
  }

  @Override
  public void processNewCommand(final TypedRecord<AuthorizationRecord> command) {
    LOG.debug(
        "Processing DELETE authorization command for key {}",
        command.getValue().getAuthorizationKey());
    permissionsBehavior
        .isAuthorized(command, PermissionType.DELETE)
        .flatMap(
            authorizationRecord ->
                permissionsBehavior.authorizationExists(
                    authorizationRecord, AUTHORIZATION_DOES_NOT_EXIST_ERROR_MESSAGE_DELETION))
        .flatMap(AuthorizationDeleteProcessor::rejectIfProtectedRoleOwner)
        .map(PersistedAuthorization::getAuthorizationKey)
        .ifRightOrLeft(
            authorizationKey -> writeEventAndDistribute(command, authorizationKey),
            (rejection) -> {
              LOG.debug("Rejecting DELETE authorization command: {}", rejection.reason());
              rejectionWriter.appendRejection(command, rejection.type(), rejection.reason());
              responseWriter.writeRejectedResponseOnCommand(
                  command, rejection.type(), rejection.reason());
            });
  }

  @Override
  public void processDistributedCommand(final TypedRecord<AuthorizationRecord> command) {
    LOG.debug(
        "Processing distributed DELETE authorization command for key {}",
        command.getValue().getAuthorizationKey());
    permissionsBehavior
        .authorizationExists(
            command.getValue(), AUTHORIZATION_DOES_NOT_EXIST_ERROR_MESSAGE_DELETION)
        .ifRightOrLeft(
            ignored -> {
              stateWriter.appendFollowUpEvent(
                  command.getValue().getAuthorizationKey(),
                  AuthorizationIntent.DELETED,
                  command.getValue());
              sideEffectWriter.appendSideEffect(
                  () -> {
                    authorizationScopeStateAdapter.invalidateAll();
                    return true;
                  });
            },
            rejection ->
                rejectionWriter.appendRejection(command, rejection.type(), rejection.reason()));

    distributionBehavior.acknowledgeCommand(command);
  }

  private static Either<Rejection, PersistedAuthorization> rejectIfProtectedRoleOwner(
      final PersistedAuthorization authorization) {
    if (authorization.getOwnerType() == AuthorizationOwnerType.ROLE) {
      final String roleId = authorization.getOwnerId();
      if (roleId != null && DefaultRole.ids().contains(roleId)) {
        return Either.left(
            new Rejection(
                RejectionType.INVALID_STATE,
                AUTHORIZATION_OWNER_PROTECTED_ERROR_MESSAGE.formatted(
                    authorization.getAuthorizationKey(), authorization.getOwnerId())));
      }
    }
    return Either.right(authorization);
  }

  private void writeEventAndDistribute(
      final TypedRecord<AuthorizationRecord> command, final long authorizationKey) {
    LOG.debug("Deleting authorization with key {}", authorizationKey);
    final long key = keyGenerator.nextKey();
    command
        .getValue()
        .setAuthorizationKey(authorizationKey)
        .setResourceMatcher(AuthorizationResourceMatcher.UNSPECIFIED);
    stateWriter.appendFollowUpEvent(
        authorizationKey, AuthorizationIntent.DELETED, command.getValue());
    distributionBehavior
        .withKey(key)
        .inQueue(DistributionQueue.IDENTITY.getQueueId())
        .distribute(command);
    responseWriter.writeAcceptedResponseOnCommand(
        authorizationKey, AuthorizationIntent.DELETED, command.getValue(), command);
    sideEffectWriter.appendSideEffect(
        () -> {
          authorizationScopeStateAdapter.invalidateAll();
          return true;
        });
  }
}
