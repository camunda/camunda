/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity;

import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.identity.authorization.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.authorization.request.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.streamprocessor.DistributedTypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.camunda.zeebe.engine.state.group.PersistedGroup;
import io.camunda.zeebe.engine.state.immutable.GroupState;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

public class GroupUpdateProcessor implements DistributedTypedRecordProcessor<GroupRecord> {

  private final GroupState groupState;
  private final KeyGenerator keyGenerator;
  private final AuthorizationCheckBehavior authCheckBehavior;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final CommandDistributionBehavior commandDistributionBehavior;

  public GroupUpdateProcessor(
      final GroupState groupState,
      final KeyGenerator keyGenerator,
      final AuthorizationCheckBehavior authCheckBehavior,
      final Writers writers,
      final CommandDistributionBehavior commandDistributionBehavior) {
    this.groupState = groupState;
    this.keyGenerator = keyGenerator;
    this.authCheckBehavior = authCheckBehavior;
    this.commandDistributionBehavior = commandDistributionBehavior;
    stateWriter = writers.state();
    responseWriter = writers.response();
    rejectionWriter = writers.rejection();
  }

  @Override
  public void processNewCommand(final TypedRecord<GroupRecord> command) {
    final var record = command.getValue();
    final var groupId = record.getGroupId();

    final var authorizationRequest =
        AuthorizationRequest.builder()
            .command(command)
            .resourceType(AuthorizationResourceType.GROUP)
            .permissionType(PermissionType.UPDATE)
            .addResourceId(groupId)
            .build();
    final var isAuthorized = authCheckBehavior.isAuthorizedOrInternalCommand(authorizationRequest);
    if (isAuthorized.isLeft()) {
      final var rejection = isAuthorized.getLeft();
      rejectionWriter.appendRejection(command, rejection.type(), rejection.reason());
      responseWriter.writeRejectionOnCommand(command, rejection.type(), rejection.reason());
      return;
    }

    final var persistedRecord = groupState.get(groupId);
    if (persistedRecord.isEmpty()) {
      final var errorMessage =
          "Expected to update group with ID '%s', but a group with this ID does not exist."
              .formatted(groupId);
      rejectionWriter.appendRejection(command, RejectionType.NOT_FOUND, errorMessage);
      responseWriter.writeRejectionOnCommand(command, RejectionType.NOT_FOUND, errorMessage);
      return;
    }

    updateExistingGroup(persistedRecord.get(), record);
    updateState(command, persistedRecord.get());

    final long distributionKey = keyGenerator.nextKey();
    commandDistributionBehavior
        .withKey(distributionKey)
        .inQueue(DistributionQueue.IDENTITY.getQueueId())
        .distribute(command);
  }

  @Override
  public void processDistributedCommand(final TypedRecord<GroupRecord> command) {
    stateWriter.appendFollowUpEvent(
        command.getValue().getGroupKey(), GroupIntent.UPDATED, command.getValue());
    commandDistributionBehavior.acknowledgeCommand(command);
  }

  private void updateExistingGroup(
      final PersistedGroup persistedGroup, final GroupRecord updateRecord) {
    final var updatedName = updateRecord.getName();
    if (!updatedName.isEmpty()) {
      persistedGroup.setName(updatedName);
    }
    final var updatedDescription = updateRecord.getDescription();

    persistedGroup.setDescription(updatedDescription);
  }

  private void updateState(
      final TypedRecord<GroupRecord> command, final PersistedGroup persistedGroup) {
    final var updatedRecord =
        new GroupRecord()
            .setGroupKey(persistedGroup.getGroupKey())
            .setGroupId(persistedGroup.getGroupId())
            .setName(persistedGroup.getName())
            .setDescription(persistedGroup.getDescription());

    stateWriter.appendFollowUpEvent(
        persistedGroup.getGroupKey(), GroupIntent.UPDATED, updatedRecord);
    responseWriter.writeEventOnCommand(
        persistedGroup.getGroupKey(), GroupIntent.UPDATED, updatedRecord, command);
    commandDistributionBehavior.acknowledgeCommand(command);
  }
}
