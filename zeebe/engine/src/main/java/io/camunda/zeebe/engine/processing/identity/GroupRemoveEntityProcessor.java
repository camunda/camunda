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
import io.camunda.zeebe.engine.state.immutable.MappingState;
import io.camunda.zeebe.engine.state.immutable.UserState;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

public class GroupRemoveEntityProcessor implements DistributedTypedRecordProcessor<GroupRecord> {
  private static final String ENTITY_NOT_ASSIGNED_ERROR_MESSAGE =
      "Expected to remove entity with key '%s' from group with key '%s', but the entity is not assigned to this group.";
  private final GroupState groupState;
  private final UserState userState;
  private final MappingState mappingState;
  private final AuthorizationCheckBehavior authCheckBehavior;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final CommandDistributionBehavior commandDistributionBehavior;

  public GroupRemoveEntityProcessor(
      final GroupState groupState,
      final UserState userState,
      final MappingState mappingState,
      final AuthorizationCheckBehavior authCheckBehavior,
      final KeyGenerator keyGenerator,
      final Writers writers,
      final CommandDistributionBehavior commandDistributionBehavior) {
    this.groupState = groupState;
    this.userState = userState;
    this.mappingState = mappingState;
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
      final var errorMessage =
          "Expected to update group with key '%s', but a group with this key does not exist."
              .formatted(groupKey);
      rejectionWriter.appendRejection(command, RejectionType.NOT_FOUND, errorMessage);
      responseWriter.writeRejectionOnCommand(command, RejectionType.NOT_FOUND, errorMessage);
      return;
    }

    final var authorizationRequest =
        new AuthorizationRequest(command, AuthorizationResourceType.GROUP, PermissionType.UPDATE)
            .addResourceId(persistedRecord.get().getName());
    final var isAuthorized = authCheckBehavior.isAuthorized(authorizationRequest);
    if (isAuthorized.isLeft()) {
      final var rejection = isAuthorized.getLeft();
      rejectionWriter.appendRejection(command, rejection.type(), rejection.reason());
      responseWriter.writeRejectionOnCommand(command, rejection.type(), rejection.reason());
      return;
    }

    final var entityKey = record.getEntityKey();
    final var entityType = record.getEntityType();
    if (!isEntityPresent(entityKey, entityType)) {
      final var errorMessage =
          "Expected to remove an entity with key '%s' and type '%s' from group with key '%s', but the entity does not exist."
              .formatted(entityKey, entityType, groupKey);
      rejectionWriter.appendRejection(command, RejectionType.NOT_FOUND, errorMessage);
      responseWriter.writeRejectionOnCommand(command, RejectionType.NOT_FOUND, errorMessage);
      return;
    }

    stateWriter.appendFollowUpEvent(groupKey, GroupIntent.ENTITY_REMOVED, record);
    responseWriter.writeEventOnCommand(groupKey, GroupIntent.ENTITY_REMOVED, record, command);

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
        .getEntityType(record.getGroupKey(), record.getEntityKey())
        .ifPresentOrElse(
            entityType ->
                stateWriter.appendFollowUpEvent(
                    command.getKey(), GroupIntent.ENTITY_REMOVED, command.getValue()),
            () -> {
              final var errorMessage =
                  ENTITY_NOT_ASSIGNED_ERROR_MESSAGE.formatted(
                      record.getEntityKey(), record.getGroupKey());
              rejectionWriter.appendRejection(command, RejectionType.NOT_FOUND, errorMessage);
            });

    commandDistributionBehavior.acknowledgeCommand(command);
  }

  private boolean isEntityPresent(final String entityKey, final EntityType entityType) {
    return switch (entityType) {
      case USER -> userState.getUser(Long.parseLong(entityKey)).isPresent();
      case MAPPING -> mappingState.get(entityKey).isPresent();
      default -> false;
    };
  }
}
