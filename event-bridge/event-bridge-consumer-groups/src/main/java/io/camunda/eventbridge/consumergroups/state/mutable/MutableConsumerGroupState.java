/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.state.mutable;

import io.camunda.eventbridge.consumergroups.state.group.GroupState;
import io.camunda.eventbridge.consumergroups.state.group.MemberState;
import io.camunda.eventbridge.consumergroups.state.immutable.ConsumerGroupState;

/**
 * Write view of the replicated consumer-group state — the event-bridge counterpart of the engine's
 * {@code MutableXxxState}. Only the appliers use this; it exposes granular put/delete primitives
 * (including explicit lifecycle-index track/untrack ops), leaving all decision logic —
 * create-if-absent, epoch bumps, state transitions, which index a group belongs in,
 * delete-when-empty — to the appliers.
 */
public interface MutableConsumerGroupState extends ConsumerGroupState {

  /** Inserts or replaces the group's row (only — the lifecycle index is maintained separately). */
  void putGroup(String groupId, GroupState group);

  /** Removes the group's row (only — the caller untracks it from the lifecycle indexes). */
  void deleteGroup(String groupId);

  /** Inserts or replaces a member's state. */
  void putMember(String groupId, String memberId, MemberState member);

  /** Removes a member. */
  void deleteMember(String groupId, String memberId);

  /**
   * Adds {@code (dueAt, groupId)} to the due-ordered rebalance index (the assignor's work list).
   */
  void trackRebalanceDue(String groupId, long dueAt);

  /** Removes {@code (dueAt, groupId)} from the due-ordered rebalance index. */
  void untrackRebalanceDue(String groupId, long dueAt);

  /** Adds the group to the {@code EMPTY} index (the retention task's work list). */
  void trackEmpty(String groupId);

  /** Removes the group from the {@code EMPTY} index. */
  void untrackEmpty(String groupId);
}
