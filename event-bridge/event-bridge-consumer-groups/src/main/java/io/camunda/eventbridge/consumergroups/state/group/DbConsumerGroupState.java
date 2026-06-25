/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.state.group;

import io.camunda.eventbridge.consumergroups.state.EventBridgeColumnFamilies;
import io.camunda.eventbridge.consumergroups.state.group.GroupSnapshot.MemberSnapshot;
import io.camunda.eventbridge.consumergroups.state.mutable.MutableConsumerGroupState;
import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.db.impl.DbString;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * RocksDB-backed {@link MutableConsumerGroupState}: per-group epochs/state/subscription keyed by
 * {@code groupId} and per-member identity/epoch/target keyed by {@code (groupId, memberId)}.
 * Rebuilt identically on every replica via stream replay, so a new coordinator leader restores
 * membership after failover.
 *
 * <p>It owns the column families and does granular storage only: get/put/delete of the group and
 * member rows, track/untrack primitives for the two lifecycle index families ({@code
 * PREPARING_REBALANCE} and {@code EMPTY} groups, which let the async tasks fetch their work without
 * scanning all groups), and immutable {@link GroupSnapshot} reads. All decision logic — create the
 * group, bump epochs, transition state, which index a group belongs in, retain/delete — lives in
 * the appliers; this class makes no decisions.
 *
 * <p>There is no in-memory mirror: each reader (the async tasks, and the coordinator via {@code
 * ConsumerGroupQueryService}) holds its own instance on a private {@link ZeebeDb} context — the
 * engine's {@code ScheduledTaskState} / {@code StateQueryService} pattern — since group membership
 * is unbounded.
 */
public final class DbConsumerGroupState implements MutableConsumerGroupState {

  private final DbString groupId = new DbString();
  private final DbString memberId = new DbString();
  private final DbCompositeKey<DbString, DbString> groupMemberKey =
      new DbCompositeKey<>(groupId, memberId);
  // (rebalanceDueAt, groupId) — the due-ordered index key; the due time leads so the scan is
  // chronological.
  private final DbLong rebalanceDueAt = new DbLong();
  private final DbCompositeKey<DbLong, DbString> rebalanceDueKey =
      new DbCompositeKey<>(rebalanceDueAt, groupId);

  private final ColumnFamily<DbString, GroupState> groupColumnFamily;
  private final ColumnFamily<DbCompositeKey<DbString, DbString>, MemberState> memberColumnFamily;
  private final ColumnFamily<DbCompositeKey<DbLong, DbString>, DbNil> rebalanceDueColumnFamily;
  private final ColumnFamily<DbString, DbNil> emptyColumnFamily;

  public DbConsumerGroupState(
      final ZeebeDb<EventBridgeColumnFamilies> zeebeDb, final TransactionContext context) {
    groupColumnFamily =
        zeebeDb.createColumnFamily(
            EventBridgeColumnFamilies.CONSUMER_GROUPS, context, groupId, new GroupState());
    memberColumnFamily =
        zeebeDb.createColumnFamily(
            EventBridgeColumnFamilies.CONSUMER_GROUP_MEMBERS,
            context,
            groupMemberKey,
            new MemberState());
    rebalanceDueColumnFamily =
        zeebeDb.createColumnFamily(
            EventBridgeColumnFamilies.CONSUMER_GROUPS_REBALANCE_DUE,
            context,
            rebalanceDueKey,
            DbNil.INSTANCE);
    emptyColumnFamily =
        zeebeDb.createColumnFamily(
            EventBridgeColumnFamilies.CONSUMER_GROUPS_EMPTY, context, groupId, DbNil.INSTANCE);
  }

  // --- reads (stream-processing actor) ----------------------------------------------------------

  @Override
  public GroupState getGroup(final String group) {
    groupId.wrapString(group);
    return groupColumnFamily.get(groupId);
  }

  @Override
  public boolean isGroupEmpty(final String group) {
    groupId.wrapString(group);
    final var empty = new boolean[] {true};
    memberColumnFamily.whileEqualPrefix(
        groupId,
        (key, value) -> {
          empty[0] = false;
          return false; // stop at the first member
        });
    return empty[0];
  }

  @Override
  public MemberState getMember(final String group, final String member) {
    groupId.wrapString(group);
    memberId.wrapString(member);
    return memberColumnFamily.get(groupMemberKey);
  }

  @Override
  public String findMemberByInstanceId(final String group, final String instanceId) {
    if (instanceId == null || instanceId.isEmpty()) {
      return null;
    }
    groupId.wrapString(group);
    final var found = new String[1];
    final BiConsumer<DbCompositeKey<DbString, DbString>, MemberState> visitor =
        (key, value) -> {
          if (instanceId.equals(value.getInstanceId())) {
            found[0] = key.second().toString();
          }
        };
    memberColumnFamily.whileEqualPrefix(groupId, visitor);
    return found[0];
  }

  @Override
  public GroupSnapshot groupSnapshot(final String group) {
    return readSnapshot(group);
  }

  @Override
  public List<GroupSnapshot> allGroups() {
    return snapshots(new ArrayList<>(groupIds()));
  }

  @Override
  public Set<String> groupIds() {
    // Materialize the keys (copied out of the shared flyweight) before returning, so the caller
    // iterates a stable set rather than reading the column family lazily downstream.
    final var ids = new HashSet<String>();
    groupColumnFamily.forEach((key, value) -> ids.add(key.toString()));
    return ids;
  }

  @Override
  public List<GroupSnapshot> rebalancesDueBy(final long now) {
    final var ids = new ArrayList<String>();
    // Ascending by due time: collect every group already due, stop at the first still in the
    // future.
    rebalanceDueColumnFamily.whileTrue(
        (key, value) -> {
          if (key.first().getValue() > now) {
            return false;
          }
          ids.add(key.second().toString());
          return true;
        });
    return snapshots(ids);
  }

  @Override
  public List<GroupSnapshot> emptyGroups() {
    return snapshots(indexedGroupIds(emptyColumnFamily));
  }

  private List<String> indexedGroupIds(final ColumnFamily<DbString, DbNil> index) {
    final var ids = new ArrayList<String>();
    index.forEach((key, value) -> ids.add(key.toString()));
    return ids;
  }

  private List<GroupSnapshot> snapshots(final List<String> groupIds) {
    final var snapshots = new ArrayList<GroupSnapshot>(groupIds.size());
    for (final var id : groupIds) {
      final var snapshot = readSnapshot(id);
      if (snapshot != null) {
        snapshots.add(snapshot);
      }
    }
    return snapshots;
  }

  private GroupSnapshot readSnapshot(final String group) {
    groupId.wrapString(group);
    final var group0 = groupColumnFamily.get(groupId);
    if (group0 == null) {
      return null;
    }
    // Copy the durable values out of the shared flyweights before returning them.
    final var groupEpoch = group0.getGroupEpoch();
    final var assignmentEpoch = group0.getAssignmentEpoch();
    final var lifecycle = group0.getState();
    final var emptySince = group0.getEmptySince();
    final var subscriptions = Map.copyOf(group0.getSubscriptions());

    final Map<String, MemberSnapshot> members = new LinkedHashMap<>();
    groupId.wrapString(group);
    final BiConsumer<DbCompositeKey<DbString, DbString>, MemberState> visitor =
        (key, value) -> {
          final var member = key.second().toString();
          members.put(
              member,
              new MemberSnapshot(
                  member,
                  value.getInstanceId(),
                  value.getMemberEpoch(),
                  value.getAssignedEpoch(),
                  value.getTargetPartitions()));
        };
    memberColumnFamily.whileEqualPrefix(groupId, visitor);
    return new GroupSnapshot(
        group,
        groupEpoch,
        assignmentEpoch,
        lifecycle,
        emptySince,
        subscriptions,
        Map.copyOf(members));
  }

  // --- writes (appliers, stream-processing actor) -----------------------------------------------
  // Granular storage only. The appliers decide what to write — including which lifecycle index a
  // group belongs in (via the track/untrack primitives below) — so no decision logic lives here.

  @Override
  public void putGroup(final String group, final GroupState value) {
    groupId.wrapString(group);
    groupColumnFamily.upsert(groupId, value);
  }

  @Override
  public void deleteGroup(final String group) {
    groupId.wrapString(group);
    groupColumnFamily.deleteIfExists(groupId);
  }

  @Override
  public void putMember(final String group, final String member, final MemberState value) {
    groupId.wrapString(group);
    memberId.wrapString(member);
    memberColumnFamily.upsert(groupMemberKey, value);
  }

  @Override
  public void deleteMember(final String group, final String member) {
    groupId.wrapString(group);
    memberId.wrapString(member);
    memberColumnFamily.deleteIfExists(groupMemberKey);
  }

  @Override
  public void trackRebalanceDue(final String group, final long dueAt) {
    rebalanceDueAt.wrapLong(dueAt);
    groupId.wrapString(group);
    rebalanceDueColumnFamily.upsert(rebalanceDueKey, DbNil.INSTANCE);
  }

  @Override
  public void untrackRebalanceDue(final String group, final long dueAt) {
    rebalanceDueAt.wrapLong(dueAt);
    groupId.wrapString(group);
    rebalanceDueColumnFamily.deleteIfExists(rebalanceDueKey);
  }

  @Override
  public void trackEmpty(final String group) {
    groupId.wrapString(group);
    emptyColumnFamily.upsert(groupId, DbNil.INSTANCE);
  }

  @Override
  public void untrackEmpty(final String group) {
    groupId.wrapString(group);
    emptyColumnFamily.deleteIfExists(groupId);
  }
}
