/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity;

import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.streamprocessor.DistributedTypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.camunda.zeebe.engine.state.immutable.AuthorizationState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.RoleState;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

public class RoleDeleteProcessor implements DistributedTypedRecordProcessor<RoleRecord> {

  private static final String ROLE_NOT_FOUND_ERROR_MESSAGE =
      "Expected to delete role with key '%s', but a role with this key doesn't exist.";
  private final RoleState roleState;
  private final AuthorizationState authorizationState;
  private final AuthorizationCheckBehavior authCheckBehavior;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final CommandDistributionBehavior commandDistributionBehavior;

  public RoleDeleteProcessor(
      final ProcessingState state,
      final AuthorizationCheckBehavior authCheckBehavior,
      final KeyGenerator keyGenerator,
      final Writers writers,
      final CommandDistributionBehavior commandDistributionBehavior) {
    roleState = state.getRoleState();
    authorizationState = state.getAuthorizationState();
    this.authCheckBehavior = authCheckBehavior;
    this.keyGenerator = keyGenerator;
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    this.commandDistributionBehavior = commandDistributionBehavior;
  }

  @Override
  public void processNewCommand(final TypedRecord<RoleRecord> command) {
    final var record = command.getValue();
    final var roleKey = record.getRoleKey();
    final var persistedRecord = roleState.getRole(roleKey);
    if (persistedRecord.isEmpty()) {
      final var errorMessage = ROLE_NOT_FOUND_ERROR_MESSAGE.formatted(roleKey);
      rejectionWriter.appendRejection(command, RejectionType.NOT_FOUND, errorMessage);
      responseWriter.writeRejectionOnCommand(command, RejectionType.NOT_FOUND, errorMessage);
      return;
    }

    final var authorizationRequest =
        new AuthorizationRequest(command, AuthorizationResourceType.ROLE, PermissionType.DELETE)
            .addResourceId(persistedRecord.get().getName());
    final var isAuthorized = authCheckBehavior.isAuthorized(authorizationRequest);
    if (isAuthorized.isLeft()) {
      final var rejection = isAuthorized.getLeft();
      rejectionWriter.appendRejection(command, rejection.type(), rejection.reason());
      responseWriter.writeRejectionOnCommand(command, rejection.type(), rejection.reason());
      return;
    }
    record.setName(persistedRecord.get().getName());
    removeMembers(record);
    deleteAuthorizations(record);
    stateWriter.appendFollowUpEvent(roleKey, RoleIntent.DELETED, record);
    responseWriter.writeEventOnCommand(roleKey, RoleIntent.DELETED, record, command);

    final long distributionKey = keyGenerator.nextKey();
    commandDistributionBehavior
        .withKey(distributionKey)
        .inQueue(DistributionQueue.IDENTITY.getQueueId())
        .distribute(command);
  }

  @Override
  public void processDistributedCommand(final TypedRecord<RoleRecord> command) {
    final var record = command.getValue();
    roleState
        .getRole(record.getRoleKey())
        .ifPresentOrElse(
            role -> {
              removeMembers(command.getValue());
              deleteAuthorizations(command.getValue());
              stateWriter.appendFollowUpEvent(
                  command.getKey(), RoleIntent.DELETED, command.getValue());
            },
            () -> {
              final var errorMessage = ROLE_NOT_FOUND_ERROR_MESSAGE.formatted(record.getRoleKey());
              rejectionWriter.appendRejection(command, RejectionType.NOT_FOUND, errorMessage);
            });

    commandDistributionBehavior.acknowledgeCommand(command);
  }

  private void removeMembers(final RoleRecord record) {
    final var roleKey = record.getRoleKey();
    roleState
        .getEntitiesByType(roleKey)
        .forEach(
            (entityType, entityKeys) -> {
              entityKeys.forEach(
                  entityKey -> {
                    final var entityRecord =
                        new RoleRecord()
                            .setRoleKey(roleKey)
                            .setEntityKey(entityKey)
                            .setEntityType(entityType);
                    stateWriter.appendFollowUpEvent(
                        roleKey, RoleIntent.ENTITY_REMOVED, entityRecord);
                  });
            });
  }

  private void deleteAuthorizations(final RoleRecord record) {
    final var roleKey = record.getRoleKey();
    final var authorizationKeysForGroup =
        authorizationState.getAuthorizationKeysForOwner(
            AuthorizationOwnerType.ROLE, String.valueOf(roleKey));

    authorizationKeysForGroup.forEach(
        authorizationKey -> {
          final var authorization = new AuthorizationRecord().setAuthorizationKey(authorizationKey);
          stateWriter.appendFollowUpEvent(
              authorizationKey, AuthorizationIntent.DELETED, authorization);
        });
  }
}
