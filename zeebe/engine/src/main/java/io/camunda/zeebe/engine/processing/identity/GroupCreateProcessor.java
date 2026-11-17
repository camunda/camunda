/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity;

import io.camunda.security.configuration.EngineSecurityConfig;
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
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class GroupCreateProcessor implements DistributedTypedRecordProcessor<GroupRecord> {

  public static final String GROUP_ALREADY_EXISTS_ERROR_MESSAGE =
      "Expected to create group with ID '%s', but a group with this ID already exists.";
  private final GroupState groupState;
  private final PermissionsBehavior permissionsBehavior;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final CommandDistributionBehavior commandDistributionBehavior;
  private final EntityMembershipsRemover membershipsRemover;
  private final SideEffectWriter sideEffectWriter;
  private final MembershipStateAdapter membershipStateAdapter;
  private final boolean camundaGroupsEnabled;

  public GroupCreateProcessor(
      final ProcessingState processingState,
      final PermissionsBehavior permissionsBehavior,
      final KeyGenerator keyGenerator,
      final Writers writers,
      final CommandDistributionBehavior commandDistributionBehavior,
      final EngineSecurityConfig securityConfig,
      final MembershipStateAdapter membershipStateAdapter) {
    groupState = processingState.getGroupState();
    this.permissionsBehavior = permissionsBehavior;
    this.keyGenerator = keyGenerator;
    this.commandDistributionBehavior = commandDistributionBehavior;
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    membershipsRemover = new EntityMembershipsRemover(processingState, stateWriter);
    sideEffectWriter = writers.sideEffect();
    this.membershipStateAdapter = membershipStateAdapter;
    // In groups-claim mode, a group ID can be assigned to roles and tenants without a Camunda
    // group,
    // so those memberships are valid and must be kept.
    camundaGroupsEnabled = securityConfig.getAuthentication().isCamundaGroupsEnabled();
  }

  @Override
  public void processNewCommand(final TypedRecord<GroupRecord> command) {
    final var isAuthorized =
        permissionsBehavior.isAuthorized(
            command, AuthorizationResourceType.GROUP, PermissionType.CREATE);

    if (isAuthorized.isLeft()) {
      final var rejection = isAuthorized.getLeft();
      rejectionWriter.appendRejection(command, rejection.type(), rejection.reason());
      responseWriter.writeRejectedResponseOnCommand(command, rejection.type(), rejection.reason());
      return;
    }

    final var record = command.getValue();
    final var groupId = record.getGroupId();
    final var persistedGroup = groupState.get(groupId);
    if (persistedGroup.isPresent()) {
      final var errorMessage = GROUP_ALREADY_EXISTS_ERROR_MESSAGE.formatted(groupId);
      rejectionWriter.appendRejection(command, RejectionType.ALREADY_EXISTS, errorMessage);
      responseWriter.writeRejectedResponseOnCommand(
          command, RejectionType.ALREADY_EXISTS, errorMessage);
      return;
    }

    final long key = keyGenerator.nextKey();
    record.setGroupKey(key);

    removeLeftoverMemberships(groupId);
    stateWriter.appendFollowUpEvent(key, GroupIntent.CREATED, record);
    responseWriter.writeAcceptedResponseOnCommand(key, GroupIntent.CREATED, record, command);

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
            () -> {
              removeLeftoverMemberships(record.getGroupId());
              stateWriter.appendFollowUpEvent(command.getKey(), GroupIntent.CREATED, record);
            });

    commandDistributionBehavior.acknowledgeCommand(command);
  }

  /**
   * Removes the memberships that are still recorded for the ID of the new group. In Camunda-groups
   * mode, only an existing group can be assigned to a role or tenant, so such memberships can only
   * be left behind by a deleted group with the same ID, and the new group must not inherit them. In
   * groups-claim mode, they are assignments of a group from the token and are kept.
   */
  private void removeLeftoverMemberships(final String groupId) {
    if (camundaGroupsEnabled
        && membershipsRemover.removeRoleAndTenantMemberships(EntityType.GROUP, groupId)) {
      sideEffectWriter.appendSideEffect(
          () -> {
            membershipStateAdapter.invalidateAll();
          });
    }
  }
}
