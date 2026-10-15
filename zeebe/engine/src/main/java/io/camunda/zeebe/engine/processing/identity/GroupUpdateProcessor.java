/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity;

import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class GroupUpdateProcessor implements DistributedTypedRecordProcessor<GroupRecord> {

  private final GroupState groupState;
  private final KeyGenerator keyGenerator;
  private final PermissionsBehavior permissionsBehavior;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final CommandDistributionBehavior commandDistributionBehavior;

  public GroupUpdateProcessor(
      final GroupState groupState,
      final KeyGenerator keyGenerator,
      final PermissionsBehavior permissionsBehavior,
      final Writers writers,
      final CommandDistributionBehavior commandDistributionBehavior) {
    this.groupState = groupState;
    this.keyGenerator = keyGenerator;
    this.permissionsBehavior = permissionsBehavior;
    this.commandDistributionBehavior = commandDistributionBehavior;
    stateWriter = writers.state();
    responseWriter = writers.response();
    rejectionWriter = writers.rejection();
  }

  @Override
  public void processNewCommand(final TypedRecord<GroupRecord> command) {
    final var record = command.getValue();
    final var groupId = record.getGroupId();

    final var isAuthorized =
        permissionsBehavior.isAuthorized(
            command, AuthorizationResourceType.GROUP, PermissionType.UPDATE, groupId);
    if (isAuthorized.isLeft()) {
      final var rejection = isAuthorized.getLeft();
      rejectionWriter.appendRejection(command, rejection.type(), rejection.reason());
      responseWriter.writeRejectedResponseOnCommand(command, rejection.type(), rejection.reason());
      return;
    }

    final var persistedRecord = groupState.get(groupId);
    if (persistedRecord.isEmpty()) {
      final var errorMessage =
          "Expected to update group with ID '%s', but a group with this ID does not exist."
              .formatted(groupId);
      rejectionWriter.appendRejection(command, RejectionType.NOT_FOUND, errorMessage);
      responseWriter.writeRejectedResponseOnCommand(command, RejectionType.NOT_FOUND, errorMessage);
      return;
    }

    updateExistingGroup(persistedRecord.get(), record);
    final var updatedRecord = updateState(command, persistedRecord.get());

    final long distributionKey = keyGenerator.nextKey();
    commandDistributionBehavior
        .withKey(distributionKey)
        .inQueue(DistributionQueue.IDENTITY.getQueueId())
        .distribute(
            command.getValueType(), command.getIntent(), updatedRecord, command.getAuthInfo());
  }

  @Override
  public void processDistributedCommand(final TypedRecord<GroupRecord> command) {
    stateWriter.appendFollowUpEvent(
        command.getValue().getGroupKey(), GroupIntent.UPDATED, command.getValue());
    commandDistributionBehavior.acknowledgeCommand(command);
  }

  private void updateExistingGroup(
      final PersistedGroup persistedGroup, final GroupRecord updateRecord) {
    final var changedAttributes = updateRecord.getChangedAttributes();

    if (changedAttributes.isEmpty()) {
      // legacy full update: it knows nothing about custom attributes, so the stored ones are kept
      final var updatedName = updateRecord.getName();
      if (!updatedName.isEmpty()) {
        persistedGroup.setName(updatedName);
      }
      persistedGroup.setDescription(updateRecord.getDescription());
      return;
    }

    for (final var attribute : changedAttributes) {
      switch (attribute) {
        case NAME -> persistedGroup.setName(updateRecord.getName());
        case DESCRIPTION -> persistedGroup.setDescription(updateRecord.getDescription());
        case EXTERNAL_ID -> persistedGroup.setExternalId(updateRecord.getExternalId());
        case ATTRIBUTES ->
            persistedGroup.setAttributes(withoutNullValues(updateRecord.getAttributes()));
        case ATTRIBUTES_MERGE ->
            persistedGroup.setAttributes(
                mergeAttributes(persistedGroup.getAttributes(), updateRecord.getAttributes()));
      }
    }
  }

  private GroupRecord updateState(
      final TypedRecord<GroupRecord> command, final PersistedGroup persistedGroup) {
    final var updatedRecord =
        new GroupRecord()
            .setGroupKey(persistedGroup.getGroupKey())
            .setGroupId(persistedGroup.getGroupId())
            .setName(persistedGroup.getName())
            .setDescription(persistedGroup.getDescription())
            .setExternalId(persistedGroup.getExternalId())
            .setAttributes(persistedGroup.getAttributes());

    stateWriter.appendFollowUpEvent(
        persistedGroup.getGroupKey(), GroupIntent.UPDATED, updatedRecord);
    responseWriter.writeAcceptedResponseOnCommand(
        persistedGroup.getGroupKey(), GroupIntent.UPDATED, updatedRecord, command);
    commandDistributionBehavior.acknowledgeCommand(command);
    return updatedRecord;
  }

  private static Map<String, Object> withoutNullValues(final Map<String, Object> attributes) {
    final Map<String, Object> result = new HashMap<>(attributes);
    result.values().removeIf(Objects::isNull);
    return result;
  }

  /** Top-level JSON merge patch: a null value removes the key, any other value sets it. */
  private static Map<String, Object> mergeAttributes(
      final Map<String, Object> stored, final Map<String, Object> patch) {
    final Map<String, Object> result = new HashMap<>(stored);
    patch.forEach(
        (key, value) -> {
          if (value == null) {
            result.remove(key);
          } else {
            result.put(key, value);
          }
        });
    return result;
  }
}
