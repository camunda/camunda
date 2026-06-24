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
 * concrete {@code Db…} class); the heartbeat handler and async assignor read the thread-safe mirror
 * ({@link #groupSnapshot}/{@link #groupSnapshots}) off the processing actor.
 *
 * <p>The returned {@link GroupState}/{@link MemberState} are the durable values; treat them as
 * read-only outside an applier (an applier may mutate one and write it back via {@link
 * MutableConsumerGroupState}).
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

  /** A thread-safe snapshot of the group's membership/assignment, or {@code null} if unknown. */
  GroupSnapshot groupSnapshot(String groupId);

  /** Thread-safe snapshots of all groups. */
  List<GroupSnapshot> groupSnapshots();
}
