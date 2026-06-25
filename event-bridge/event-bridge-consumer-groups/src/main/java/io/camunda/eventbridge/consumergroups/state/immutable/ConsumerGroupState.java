/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.state.immutable;

import io.camunda.eventbridge.consumergroups.state.group.GroupSnapshot;
import io.camunda.eventbridge.consumergroups.state.group.GroupState;
import io.camunda.eventbridge.consumergroups.state.group.MemberState;
import io.camunda.eventbridge.consumergroups.state.mutable.MutableConsumerGroupState;
import java.util.List;

/**
 * Read view of the replicated consumer-group state — the event-bridge counterpart of the engine's
 * immutable {@code XxxState} interfaces. Processors and validators depend on this (never on the
 * concrete {@code Db…} class). It is used both on the stream-processing actor (by processors) and
 * off it: the async tasks each hold their own instance on a private context (mirroring the engine's
 * {@code ScheduledTaskState}), and the coordinator reads through {@code ConsumerGroupQueryService},
 * which wraps an instance of this. There is no in-memory mirror.
 *
 * <p>The returned {@link GroupState}/{@link MemberState} are the durable values; treat them as
 * read-only outside an applier (an applier may mutate one and write it back via {@link
 * MutableConsumerGroupState}). The {@link GroupSnapshot} reads, by contrast, return immutable
 * copies safe to hand to another component.
 */
public interface ConsumerGroupState {

  /** The group's state, or {@code null} if the group does not exist. */
  GroupState getGroup(String groupId);

  /** Whether the group has no members (true also for an unknown group). */
  boolean isGroupEmpty(String groupId);

  /** The member's state, or {@code null} if the member does not exist. */
  MemberState getMember(String groupId, String memberId);

  /** The member id of the static member with the given instance id, or {@code null} if none. */
  String findMemberByInstanceId(String groupId, String instanceId);

  /** An immutable snapshot of a group (with its members), or {@code null} if it does not exist. */
  GroupSnapshot groupSnapshot(String groupId);

  /** Snapshots of every group — a full scan, for the coordinator's describe/seed reads. */
  List<GroupSnapshot> allGroups();

  /**
   * Snapshots of the groups whose debounced rebalance is due at or before {@code now}, in ascending
   * due order, read from the due-ordered index (no full scan).
   */
  List<GroupSnapshot> rebalancesDueBy(long now);

  /** Snapshots of the {@code EMPTY} groups, read from the index (no full scan). */
  List<GroupSnapshot> emptyGroups();
}
