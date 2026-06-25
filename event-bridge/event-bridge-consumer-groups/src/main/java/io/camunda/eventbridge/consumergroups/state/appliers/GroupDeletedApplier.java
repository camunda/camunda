/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.state.appliers;

import io.camunda.eventbridge.consumergroups.record.MembershipRecord;
import io.camunda.eventbridge.consumergroups.state.mutable.MutableConsumerGroupState;
import io.camunda.eventbridge.consumergroups.state.mutable.MutableOffsetState;
import io.camunda.eventbridge.stream.TypedEventApplier;
import io.camunda.zeebe.protocol.record.intent.CoordinatorIntent;

/**
 * Applies {@code GROUP_DELETED}: removes a reclaimed (empty) group and its committed offsets. The
 * group has no members at this point (it was {@code EMPTY}), so only the group row and its offsets
 * remain to delete. Runs identically on leader (after {@code DeleteGroupProcessor}) and follower
 * (on replay).
 */
public final class GroupDeletedApplier
    implements TypedEventApplier<CoordinatorIntent, MembershipRecord> {

  private final MutableConsumerGroupState groupState;
  private final MutableOffsetState offsetState;

  public GroupDeletedApplier(
      final MutableConsumerGroupState groupState, final MutableOffsetState offsetState) {
    this.groupState = groupState;
    this.offsetState = offsetState;
  }

  @Override
  public void applyState(final long key, final MembershipRecord value) {
    final var groupId = value.getGroupId();
    // The group was EMPTY (in the retention index, and never in the rebalance-due index); drop its
    // retention-index entry, its row, and its offsets.
    groupState.untrackEmpty(groupId);
    groupState.deleteGroup(groupId);
    offsetState.deleteGroupOffsets(groupId);
  }
}
