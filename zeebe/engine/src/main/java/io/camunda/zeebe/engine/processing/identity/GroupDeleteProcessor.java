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
import io.camunda.zeebe.engine.state.authorization.DbMembershipState.RelationType;
import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.camunda.zeebe.engine.state.immutable.AuthorizationState;
import io.camunda.zeebe.engine.state.immutable.GroupState;
import io.camunda.zeebe.engine.state.immutable.MembershipState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class GroupDeleteProcessor implements DistributedTypedRecordProcessor<GroupRecord> {

  private final GroupState groupState;
  private final GroupValidator groupValidator;
  private final EntityMembershipsRemover membershipsRemover;
  private final AuthorizationState authorizationState;
  private final MembershipState membershipState;
  private final PermissionsBehavior permissionsBehavior;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final CommandDistributionBehavior commandDistributionBehavior;
  private final SideEffectWriter sideEffectWriter;
  private final MembershipStateAdapter membershipStateAdapter;

  public GroupDeleteProcessor(
      final ProcessingState processingState,
      final GroupValidator groupValidator,
      final PermissionsBehavior permissionsBehavior,
      final KeyGenerator keyGenerator,
      final Writers writers,
      final CommandDistributionBehavior commandDistributionBehavior,
      final MembershipStateAdapter membershipStateAdapter) {
    groupState = processingState.getGroupState();
    this.groupValidator = groupValidator;
    authorizationState = processingState.getAuthorizationState();
    membershipState = processingState.getMembershipState();
    this.permissionsBehavior = permissionsBehavior;
    this.keyGenerator = keyGenerator;
    stateWriter = writers.state();
    membershipsRemover = new EntityMembershipsRemover(processingState, stateWriter);
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    this.commandDistributionBehavior = commandDistributionBehavior;
    sideEffectWriter = writers.sideEffect();
    this.membershipStateAdapter = membershipStateAdapter;
  }

  @Override
  public void processNewCommand(final TypedRecord<GroupRecord> command) {
    final var record = command.getValue();
    final var groupId = record.getGroupId();
    final var isAuthorized =
        permissionsBehavior.isAuthorized(
            command, AuthorizationResourceType.GROUP, PermissionType.DELETE, groupId);
    if (isAuthorized.isLeft()) {
      reject(command, isAuthorized.getLeft());
      return;
    }

    final var validation = groupValidator.validateDelete(record);
    if (validation.isLeft()) {
      reject(command, validation.getLeft());
      return;
    }

    final var persistedGroup = groupState.get(groupId).orElseThrow();
    final var groupKey = persistedGroup.getGroupKey();
    record.setGroupKey(groupKey);

    removeAssignedEntities(record);
    membershipsRemover.removeRoleAndTenantMemberships(EntityType.GROUP, record.getGroupId());
    deleteAuthorizations(record);

    stateWriter.appendFollowUpEvent(groupKey, GroupIntent.DELETED, record);
    responseWriter.writeAcceptedResponseOnCommand(groupKey, GroupIntent.DELETED, record, command);
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
        .validateDelete(command.getValue())
        .ifRightOrLeft(
            record -> {
              removeAssignedEntities(record);
              membershipsRemover.removeRoleAndTenantMemberships(
                  EntityType.GROUP, record.getGroupId());
              deleteAuthorizations(record);
              stateWriter.appendFollowUpEvent(command.getKey(), GroupIntent.DELETED, record);
              invalidateMembershipCache();
            },
            rejection ->
                rejectionWriter.appendRejection(command, rejection.type(), rejection.reason()));

    commandDistributionBehavior.acknowledgeCommand(command);
  }

  /**
   * Flushes {@link MembershipStateAdapter}'s membership cache after the group deletion is
   * committed, so that no authorization check uses the memberships that the deletion removed.
   */
  private void invalidateMembershipCache() {
    sideEffectWriter.appendSideEffect(
        () -> {
          membershipStateAdapter.invalidateAll();
        });
  }

  private void removeAssignedEntities(final GroupRecord record) {
    final var groupId = record.getGroupId();
    membershipState.forEachMember(
        RelationType.GROUP,
        groupId,
        (entityType, entityId) -> {
          stateWriter.appendFollowUpEvent(
              record.getGroupKey(),
              GroupIntent.ENTITY_REMOVED,
              new GroupRecord()
                  .setGroupId(groupId)
                  .setEntityType(entityType)
                  .setEntityId(entityId));
        });
  }

  private void deleteAuthorizations(final GroupRecord record) {
    final var groupId = record.getGroupId();
    final var authorizationKeysForGroup =
        authorizationState.getAuthorizationKeysForOwner(AuthorizationOwnerType.GROUP, groupId);

    authorizationKeysForGroup.forEach(
        authorizationKey -> {
          final var authorization =
              new AuthorizationRecord()
                  .setAuthorizationKey(authorizationKey)
                  .setResourceMatcher(AuthorizationResourceMatcher.UNSPECIFIED);
          stateWriter.appendFollowUpEvent(
              authorizationKey, AuthorizationIntent.DELETED, authorization);
        });
  }

  private void reject(final TypedRecord<GroupRecord> command, final Rejection rejection) {
    rejectionWriter.appendRejection(command, rejection.type(), rejection.reason());
    responseWriter.writeRejectedResponseOnCommand(command, rejection.type(), rejection.reason());
  }
}
