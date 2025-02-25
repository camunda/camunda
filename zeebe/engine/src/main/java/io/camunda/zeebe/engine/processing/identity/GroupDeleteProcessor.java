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
import io.camunda.zeebe.engine.state.immutable.GroupState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

public class GroupDeleteProcessor implements DistributedTypedRecordProcessor<GroupRecord> {

  private static final String GROUP_NOT_FOUND_ERROR_MESSAGE =
      "Expected to delete group with key '%s', but a group with this key does not exist.";
  private final GroupState groupState;
  private final AuthorizationState authorizationState;
  private final AuthorizationCheckBehavior authCheckBehavior;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final CommandDistributionBehavior commandDistributionBehavior;

  public GroupDeleteProcessor(
      final ProcessingState processingState,
      final AuthorizationCheckBehavior authCheckBehavior,
      final KeyGenerator keyGenerator,
      final Writers writers,
      final CommandDistributionBehavior commandDistributionBehavior) {
    groupState = processingState.getGroupState();
    authorizationState = processingState.getAuthorizationState();
    this.authCheckBehavior = authCheckBehavior;
    this.keyGenerator = keyGenerator;
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    this.commandDistributionBehavior = commandDistributionBehavior;
  }

  @Override
  public void processNewCommand(final TypedRecord<GroupRecord> command) {
    final var record = command.getValue();
    final var groupKey = record.getGroupKey();
    final var persistedRecord = groupState.get(groupKey);
    if (persistedRecord.isEmpty()) {
      final var errorMessage = GROUP_NOT_FOUND_ERROR_MESSAGE.formatted(groupKey);
      rejectionWriter.appendRejection(command, RejectionType.NOT_FOUND, errorMessage);
      responseWriter.writeRejectionOnCommand(command, RejectionType.NOT_FOUND, errorMessage);
      return;
    }

    final var authorizationRequest =
        new AuthorizationRequest(command, AuthorizationResourceType.GROUP, PermissionType.DELETE)
            .addResourceId(persistedRecord.get().getName());
    final var isAuthorized = authCheckBehavior.isAuthorized(authorizationRequest);
    if (isAuthorized.isLeft()) {
      final var rejection = isAuthorized.getLeft();
      rejectionWriter.appendRejection(command, rejection.type(), rejection.reason());
      responseWriter.writeRejectionOnCommand(command, rejection.type(), rejection.reason());
      return;
    }
    record.setName(persistedRecord.get().getName());

    removeAssignedEntities(record);
    deleteAuthorizations(record);

    stateWriter.appendFollowUpEvent(groupKey, GroupIntent.DELETED, record);
    responseWriter.writeEventOnCommand(groupKey, GroupIntent.DELETED, record, command);

    final long distributionKey = keyGenerator.nextKey();
    commandDistributionBehavior
        .withKey(distributionKey)
        .inQueue(DistributionQueue.IDENTITY.getQueueId())
        .distribute(command);
  }

  @Override
  public void processDistributedCommand(final TypedRecord<GroupRecord> command) {
    final var record = command.getValue();
    groupState
        .get(record.getGroupKey())
        .ifPresentOrElse(
            group -> {
              removeAssignedEntities(command.getValue());
              deleteAuthorizations(command.getValue());
              stateWriter.appendFollowUpEvent(command.getKey(), GroupIntent.DELETED, record);
            },
            () -> {
              final var errorMessage =
                  GROUP_NOT_FOUND_ERROR_MESSAGE.formatted(record.getGroupKey());
              rejectionWriter.appendRejection(command, RejectionType.NOT_FOUND, errorMessage);
            });

    commandDistributionBehavior.acknowledgeCommand(command);
  }

  private void removeAssignedEntities(final GroupRecord record) {
    final var groupKey = record.getGroupKey();
    groupState
        .getEntitiesByType(groupKey)
        .forEach(
            (entityType, entityKeys) -> {
              entityKeys.forEach(
                  entityKey -> {
                    final var entityRecord =
                        new GroupRecord()
                            .setGroupKey(groupKey)
                            .setEntityKey(entityKey)
                            .setEntityType(entityType);
                    stateWriter.appendFollowUpEvent(
                        groupKey, GroupIntent.ENTITY_REMOVED, entityRecord);
                  });
            });
  }

  private void deleteAuthorizations(final GroupRecord record) {
    final var groupKey = record.getGroupKey();
    final var authorizationKeysForGroup =
        authorizationState.getAuthorizationKeysForOwner(
            AuthorizationOwnerType.GROUP, String.valueOf(groupKey));

    authorizationKeysForGroup.forEach(
        authorizationKey -> {
          final var authorization = new AuthorizationRecord().setAuthorizationKey(authorizationKey);
          stateWriter.appendFollowUpEvent(
              authorizationKey, AuthorizationIntent.DELETED, authorization);
        });
  }
}
