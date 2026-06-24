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
 * {@code MutableXxxState}. Only the appliers use this; it exposes granular put/delete primitives,
 * leaving the decision logic (create-if-absent, epoch bumps, delete-when-empty) to the appliers.
 */
public interface MutableConsumerGroupState extends ConsumerGroupState {

  /** Inserts or replaces the group's state (and refreshes its mirror snapshot). */
  void putGroup(String groupId, GroupState group);

  /** Removes the group and its mirror snapshot. */
  void deleteGroup(String groupId);

  /** Inserts or replaces a member's state (and refreshes the group's mirror snapshot). */
  void putMember(String groupId, String memberId, MemberState member);

  /** Removes a member (and refreshes the group's mirror snapshot). */
  void deleteMember(String groupId, String memberId);

  /** Rebuilds the mirror from durable state before processing starts (no concurrent access yet). */
  void seedMirror();
}
