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
import io.camunda.zeebe.engine.state.authorization.DbMembershipState.RelationType;
import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.camunda.zeebe.engine.state.immutable.GroupState;
import io.camunda.zeebe.engine.state.immutable.MappingRuleState;
import io.camunda.zeebe.engine.state.immutable.MembershipState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.UserState;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.util.LinkedHashSet;
import java.util.Set;
import org.jspecify.annotations.NullMarked;

/**
 * Replaces the complete membership of one entity type of a group. The difference to the current
 * membership is computed against the primary state and emitted as the regular {@link
 * GroupIntent#ENTITY_ADDED} and {@link GroupIntent#ENTITY_REMOVED} events, followed by a final
 * {@link GroupIntent#ENTITIES_SET} event. Members of other entity types are not touched.
 *
 * <p>The full member list travels in a single command and is distributed as is, so it is bounded by
 * the maximum message size. Every partition computes the difference against its own state.
 */
@NullMarked
public class GroupSetEntitiesProcessor implements DistributedTypedRecordProcessor<GroupRecord> {

  private final GroupState groupState;
  private final MappingRuleState mappingRuleState;
  private final UserState userState;
  private final MembershipState membershipState;
  private final PermissionsBehavior permissionsBehavior;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final SideEffectWriter sideEffectWriter;
  private final CommandDistributionBehavior commandDistributionBehavior;
  private final EngineSecurityConfig securityConfig;
  private final MembershipStateAdapter membershipStateAdapter;

  public GroupSetEntitiesProcessor(
      final ProcessingState processingState,
      final PermissionsBehavior permissionsBehavior,
      final KeyGenerator keyGenerator,
      final Writers writers,
      final CommandDistributionBehavior commandDistributionBehavior,
      final EngineSecurityConfig securityConfig,
      final MembershipStateAdapter membershipStateAdapter) {
    groupState = processingState.getGroupState();
    mappingRuleState = processingState.getMappingRuleState();
    userState = processingState.getUserState();
    membershipState = processingState.getMembershipState();
    this.permissionsBehavior = permissionsBehavior;
    this.keyGenerator = keyGenerator;
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    sideEffectWriter = writers.sideEffect();
    this.commandDistributionBehavior = commandDistributionBehavior;
    this.securityConfig = securityConfig;
    this.membershipStateAdapter = membershipStateAdapter;
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

    final var persistedGroup = groupState.get(groupId);
    if (persistedGroup.isEmpty()) {
      final var errorMessage =
          "Expected to set entities of group with ID '%s', but a group with this ID does not exist."
              .formatted(groupId);
      reject(command, RejectionType.NOT_FOUND, errorMessage);
      return;
    }

    final var entityType = record.getEntityType();
    if (!isSupportedType(entityType)) {
      final var errorMessage =
          "Expected to set entities of group with ID '%s', but the entity type '%s' is not supported."
              .formatted(groupId, entityType);
      reject(command, RejectionType.INVALID_ARGUMENT, errorMessage);
      return;
    }

    final var targetIds = new LinkedHashSet<>(record.getEntityIds());
    for (final var entityId : targetIds) {
      if (!isEntityPresent(entityId, entityType)) {
        final var errorMessage =
            "Expected to set an entity with ID '%s' and type '%s' on group with ID '%s', but the entity does not exist."
                .formatted(entityId, entityType, groupId);
        reject(command, RejectionType.NOT_FOUND, errorMessage);
        return;
      }
    }

    final var groupKey = persistedGroup.get().getGroupKey();
    final var entitiesSet = appendEvents(groupKey, groupId, entityType, targetIds);
    responseWriter.writeAcceptedResponseOnCommand(
        groupKey, GroupIntent.ENTITIES_SET, entitiesSet, command);

    final long distributionKey = keyGenerator.nextKey();
    commandDistributionBehavior
        .withKey(distributionKey)
        .inQueue(DistributionQueue.IDENTITY.getQueueId())
        .distribute(command);
  }

  @Override
  public void processDistributedCommand(final TypedRecord<GroupRecord> command) {
    final var record = command.getValue();
    final var groupId = record.getGroupId();

    if (groupState.get(groupId).isPresent()) {
      appendEvents(
          command.getKey(),
          groupId,
          record.getEntityType(),
          new LinkedHashSet<>(record.getEntityIds()));
    } else {
      final var errorMessage =
          "Expected to set entities of group with ID '%s', but a group with this ID does not exist."
              .formatted(groupId);
      rejectionWriter.appendRejection(command, RejectionType.NOT_FOUND, errorMessage);
    }

    commandDistributionBehavior.acknowledgeCommand(command);
  }

  private GroupRecord appendEvents(
      final long key,
      final String groupId,
      final EntityType entityType,
      final Set<String> targetIds) {
    final Set<String> currentIds = new LinkedHashSet<>();
    membershipState.forEachMember(
        RelationType.GROUP,
        groupId,
        (type, id) -> {
          if (type == entityType) {
            currentIds.add(id);
          }
        });

    for (final var id : currentIds) {
      if (!targetIds.contains(id)) {
        stateWriter.appendFollowUpEvent(
            key,
            GroupIntent.ENTITY_REMOVED,
            new GroupRecord().setGroupId(groupId).setEntityId(id).setEntityType(entityType));
      }
    }
    for (final var id : targetIds) {
      if (!currentIds.contains(id)) {
        stateWriter.appendFollowUpEvent(
            key,
            GroupIntent.ENTITY_ADDED,
            new GroupRecord().setGroupId(groupId).setEntityId(id).setEntityType(entityType));
      }
    }

    final var entitiesSet =
        new GroupRecord().setGroupId(groupId).setEntityType(entityType).setEntityIds(targetIds);
    stateWriter.appendFollowUpEvent(key, GroupIntent.ENTITIES_SET, entitiesSet);
    invalidateMembershipCache();
    return entitiesSet;
  }

  private void reject(
      final TypedRecord<GroupRecord> command, final RejectionType type, final String reason) {
    rejectionWriter.appendRejection(command, type, reason);
    responseWriter.writeRejectedResponseOnCommand(command, type, reason);
  }

  /**
   * Flushes {@link MembershipStateAdapter}'s membership cache after a group-membership change, so a
   * stale entry doesn't authorize (or reject) against outdated membership until its TTL expires.
   */
  private void invalidateMembershipCache() {
    sideEffectWriter.appendSideEffect(membershipStateAdapter::invalidateAll);
  }

  private boolean isSupportedType(final EntityType entityType) {
    return switch (entityType) {
      case USER, CLIENT, MAPPING_RULE -> true;
      default -> false;
    };
  }

  private boolean isEntityPresent(final String entityId, final EntityType entityType) {
    final boolean localUserEnabled = securityConfig.getAuthentication().isCamundaUsersEnabled();
    return switch (entityType) {
      case USER -> !localUserEnabled || userState.getUser(entityId).isPresent();
      case CLIENT -> true;
      case MAPPING_RULE -> mappingRuleState.get(entityId).isPresent();
      default -> false;
    };
  }
}
