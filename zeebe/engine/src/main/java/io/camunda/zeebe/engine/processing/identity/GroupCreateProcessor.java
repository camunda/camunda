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
import io.camunda.zeebe.engine.state.immutable.GroupState;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

public class GroupCreateProcessor implements DistributedTypedRecordProcessor<GroupRecord> {

  public static final String GROUP_ALREADY_EXISTS_ERROR_MESSAGE =
      "Expected to create group with ID '%s', but a group with this ID already exists.";
  private final GroupState groupState;
  private final AuthorizationCheckBehavior authCheckBehavior;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final CommandDistributionBehavior commandDistributionBehavior;

  public GroupCreateProcessor(
      final GroupState groupState,
      final AuthorizationCheckBehavior authCheckBehavior,
      final KeyGenerator keyGenerator,
      final Writers writers,
      final CommandDistributionBehavior commandDistributionBehavior) {
    this.groupState = groupState;
    this.authCheckBehavior = authCheckBehavior;
    this.keyGenerator = keyGenerator;
    this.commandDistributionBehavior = commandDistributionBehavior;
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
  }

  @Override
  public void processNewCommand(final TypedRecord<GroupRecord> command) {
    final var authorizationRequest =
        new AuthorizationRequest(command, AuthorizationResourceType.GROUP, PermissionType.CREATE);
    final var isAuthorized = authCheckBehavior.isAuthorized(authorizationRequest);

    if (isAuthorized.isLeft()) {
      final var rejection = isAuthorized.getLeft();
      rejectionWriter.appendRejection(command, rejection.type(), rejection.reason());
      responseWriter.writeRejectionOnCommand(command, rejection.type(), rejection.reason());
      return;
    }

    final var record = command.getValue();
    final var groupId = record.getGroupId();
    final var persistedGroup = groupState.get(groupId);
    if (persistedGroup.isPresent()) {
      final var errorMessage = GROUP_ALREADY_EXISTS_ERROR_MESSAGE.formatted(groupId);
      rejectionWriter.appendRejection(command, RejectionType.ALREADY_EXISTS, errorMessage);
      responseWriter.writeRejectionOnCommand(command, RejectionType.ALREADY_EXISTS, errorMessage);
      return;
    }

    final long key = keyGenerator.nextKey();
    record.setGroupKey(key);

    stateWriter.appendFollowUpEvent(key, GroupIntent.CREATED, record);
    responseWriter.writeEventOnCommand(key, GroupIntent.CREATED, record, command);

    commandDistributionBehavior
        .withKey(key)
        .inQueue(DistributionQueue.IDENTITY.getQueueId())
        .distribute(command);
  }

  @Override
  public void processDistributedCommand(final TypedRecord<GroupRecord> command) {
    final var record = command.getValue();
    groupState
        .get(record.getGroupId())
        .ifPresentOrElse(
            persistedGroup -> {
              final var errorMessage =
                  GROUP_ALREADY_EXISTS_ERROR_MESSAGE.formatted(persistedGroup.getGroupId());
              rejectionWriter.appendRejection(command, RejectionType.ALREADY_EXISTS, errorMessage);
            },
            () -> stateWriter.appendFollowUpEvent(command.getKey(), GroupIntent.CREATED, record));

    commandDistributionBehavior.acknowledgeCommand(command);
  }
}
