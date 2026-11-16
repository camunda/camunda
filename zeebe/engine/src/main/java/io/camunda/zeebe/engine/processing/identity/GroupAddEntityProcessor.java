/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity;

import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.identity.adapter.MembershipStateAdapter;
import io.camunda.zeebe.engine.processing.streamprocessor.DistributedTypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.SideEffectWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.camunda.zeebe.engine.state.immutable.GroupState;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class GroupAddEntityProcessor implements DistributedTypedRecordProcessor<GroupRecord> {
  private final GroupState groupState;
  private final GroupValidator groupValidator;
  private final PermissionsBehavior permissionsBehavior;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final CommandDistributionBehavior commandDistributionBehavior;
  private final SideEffectWriter sideEffectWriter;
  private final MembershipStateAdapter membershipStateAdapter;

  public GroupAddEntityProcessor(
      final GroupState groupState,
      final GroupValidator groupValidator,
      final PermissionsBehavior permissionsBehavior,
      final KeyGenerator keyGenerator,
      final Writers writers,
      final CommandDistributionBehavior commandDistributionBehavior,
      final MembershipStateAdapter membershipStateAdapter) {
    this.commandDistributionBehavior = commandDistributionBehavior;
    this.keyGenerator = keyGenerator;
    this.permissionsBehavior = permissionsBehavior;
    this.groupState = groupState;
    this.groupValidator = groupValidator;
    stateWriter = writers.state();
    responseWriter = writers.response();
    rejectionWriter = writers.rejection();
    sideEffectWriter = writers.sideEffect();
    this.membershipStateAdapter = membershipStateAdapter;
  }

  @Override
  public void processNewCommand(final TypedRecord<GroupRecord> command) {
    final var record = command.getValue();
    final var isAuthorized =
        permissionsBehavior.isAuthorized(
            command, AuthorizationResourceType.GROUP, PermissionType.UPDATE, record.getGroupId());
    if (isAuthorized.isLeft()) {
      reject(command, isAuthorized.getLeft());
      return;
    }

    final var validation = groupValidator.validateAddEntity(record);
    if (validation.isLeft()) {
      reject(command, validation.getLeft());
      return;
    }

    final var groupKey = groupState.get(record.getGroupId()).orElseThrow().getGroupKey();

    stateWriter.appendFollowUpEvent(groupKey, GroupIntent.ENTITY_ADDED, record);
    responseWriter.writeAcceptedResponseOnCommand(
        groupKey, GroupIntent.ENTITY_ADDED, record, command);
    invalidateMembershipCache();

    final long distributionKey = keyGenerator.nextKey();
    commandDistributionBehavior
        .withKey(distributionKey)
        .inQueue(DistributionQueue.IDENTITY.getQueueId())
        .distribute(command);
  }

  @Override
  public void processDistributedCommand(final TypedRecord<GroupRecord> command) {
    groupValidator
        .validateEntityNotAssigned(command.getValue())
        .ifRightOrLeft(
            record -> {
              stateWriter.appendFollowUpEvent(command.getKey(), GroupIntent.ENTITY_ADDED, record);
              invalidateMembershipCache();
            },
            rejection ->
                rejectionWriter.appendRejection(command, rejection.type(), rejection.reason()));

    commandDistributionBehavior.acknowledgeCommand(command);
  }

  /**
   * Flushes {@link MembershipStateAdapter}'s membership cache after a group-membership change, so a
   * stale entry doesn't authorize (or reject) against outdated membership until its TTL expires.
   */
  private void invalidateMembershipCache() {
    sideEffectWriter.appendSideEffect(
        () -> {
          membershipStateAdapter.invalidateAll();
        });
  }

  private void reject(final TypedRecord<GroupRecord> command, final Rejection rejection) {
    rejectionWriter.appendRejection(command, rejection.type(), rejection.reason());
    responseWriter.writeRejectedResponseOnCommand(command, rejection.type(), rejection.reason());
  }
}
