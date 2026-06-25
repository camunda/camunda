/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.state.appliers;

import io.camunda.eventbridge.consumergroups.record.RebalanceRecord;
import io.camunda.eventbridge.consumergroups.state.group.GroupLifecycle;
import io.camunda.eventbridge.consumergroups.state.mutable.MutableConsumerGroupState;
import io.camunda.eventbridge.stream.TypedEventApplier;
import io.camunda.zeebe.protocol.record.intent.CoordinatorIntent;

/**
 * Applies {@code GROUP_REBALANCED}: advances the group's assignment epoch and sets each member's
 * target partitions (skipping members that left since the assignor computed the target). The
 * decision logic lives here. Runs identically on leader (after {@code RebalanceProcessor}) and
 * follower (on replay).
 */
public final class GroupRebalancedApplier
    implements TypedEventApplier<CoordinatorIntent, RebalanceRecord> {

  private final MutableConsumerGroupState state;

  public GroupRebalancedApplier(final MutableConsumerGroupState state) {
    this.state = state;
  }

  @Override
  public void applyState(final long key, final RebalanceRecord value) {
    final var groupId = value.getGroupId();
    final var group = state.getGroup(groupId);
    if (group == null) {
      return;
    }
    group.setAssignmentEpoch(value.getAssignmentEpoch());
    // The target is committed but members have not yet confirmed reconciling to it — the group is
    // RECONCILING until every member's assignedEpoch catches up (MEMBER_RECONCILED → STABLE).
    group.setState(GroupLifecycle.RECONCILING);
    state.putGroup(groupId, group);

    value
        .getMembers()
        .forEach(
            (memberId, partitions) -> {
              final var member = state.getMember(groupId, memberId);
              if (member != null) {
                member.setTargetPartitions(partitions);
                state.putMember(groupId, memberId, member);
              }
            });
  }
}
