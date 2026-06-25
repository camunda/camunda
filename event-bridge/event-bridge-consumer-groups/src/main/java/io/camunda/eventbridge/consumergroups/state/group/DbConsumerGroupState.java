/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.state.group;

import io.camunda.eventbridge.consumergroups.state.EventBridgeColumnFamilies;
import io.camunda.eventbridge.consumergroups.state.mutable.MutableConsumerGroupState;
import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.db.impl.DbString;
import java.util.function.BiConsumer;

/**
 * RocksDB-backed {@link MutableConsumerGroupState}: per-group epochs/state/subscription keyed by
 * {@code groupId} and per-member identity/epoch/target keyed by {@code (groupId, memberId)}.
 * Rebuilt identically on every replica via stream replay, so a new coordinator leader restores
 * membership after failover.
 *
 * <p>This class only does granular storage on the stream-processing actor — get/put/delete plus
 * upkeep of two lifecycle index families ({@code PREPARING_REBALANCE} and {@code EMPTY} groups) so
 * the async tasks fetch their work without scanning all groups. The decision logic (create the
 * group, bump epochs, transition state, retain/delete) lives in the appliers.
 *
 * <p>There is no in-memory mirror: off-actor reads (heartbeat handler, async tasks, describe) go
 * through {@code ConsumerGroupQueryService} (a separate {@link ZeebeDb} context) instead of a heap
 * projection, since group membership is unbounded.
 */
public final class DbConsumerGroupState implements MutableConsumerGroupState {

  private final DbString groupId = new DbString();
  private final DbString memberId = new DbString();
  private final DbCompositeKey<DbString, DbString> groupMemberKey =
      new DbCompositeKey<>(groupId, memberId);

  private final ColumnFamily<DbString, GroupState> groupColumnFamily;
  private final ColumnFamily<DbCompositeKey<DbString, DbString>, MemberState> memberColumnFamily;
  private final ColumnFamily<DbString, DbNil> pendingRebalanceColumnFamily;
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
    pendingRebalanceColumnFamily =
        zeebeDb.createColumnFamily(
            EventBridgeColumnFamilies.CONSUMER_GROUPS_PENDING_REBALANCE,
            context,
            groupId,
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
  public boolean allMembersReconciled(final String group, final long epoch) {
    groupId.wrapString(group);
    final var allReconciled = new boolean[] {true};
    memberColumnFamily.whileEqualPrefix(
        groupId,
        (key, value) -> {
          if (value.getAssignedEpoch() != epoch) {
            allReconciled[0] = false;
            return false; // stop at the first lagging member
          }
          return true;
        });
    return allReconciled[0];
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

  // --- writes (appliers, stream-processing actor) -----------------------------------------------

  @Override
  public void putGroup(final String group, final GroupState value) {
    groupId.wrapString(group);
    groupColumnFamily.upsert(groupId, value);
    updateLifecycleIndex(group, value.getState());
  }

  @Override
  public void deleteGroup(final String group) {
    groupId.wrapString(group);
    groupColumnFamily.deleteIfExists(groupId);
    pendingRebalanceColumnFamily.deleteIfExists(groupId);
    emptyColumnFamily.deleteIfExists(groupId);
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

  // --- internals --------------------------------------------------------------------------------

  /**
   * Keeps the lifecycle index families in sync with a group's state, so the assignor/retention
   * tasks can list just the {@code PREPARING_REBALANCE} / {@code EMPTY} groups.
   */
  private void updateLifecycleIndex(final String group, final GroupLifecycle state) {
    groupId.wrapString(group);
    if (state == GroupLifecycle.PREPARING_REBALANCE) {
      pendingRebalanceColumnFamily.upsert(groupId, DbNil.INSTANCE);
      emptyColumnFamily.deleteIfExists(groupId);
    } else if (state == GroupLifecycle.EMPTY) {
      emptyColumnFamily.upsert(groupId, DbNil.INSTANCE);
      pendingRebalanceColumnFamily.deleteIfExists(groupId);
    } else {
      pendingRebalanceColumnFamily.deleteIfExists(groupId);
      emptyColumnFamily.deleteIfExists(groupId);
    }
  }
}
