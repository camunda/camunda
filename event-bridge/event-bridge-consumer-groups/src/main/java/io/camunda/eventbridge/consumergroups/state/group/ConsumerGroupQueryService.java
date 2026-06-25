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
import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.db.impl.DbString;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * A read-only view of consumer-group state, built straight from RocksDB on its <em>own</em> {@link
 * ZeebeDb} context — the event-bridge counterpart of Zeebe's {@code StateQueryService} (and the
 * sibling of {@code OffsetQueryService}). Group membership is unbounded (groups × members × target
 * partitions), so it is <b>not</b> kept in an in-memory mirror; the coordinator and the async tasks
 * read it on demand off the stream-processing actor instead.
 *
 * <p>Periodic tasks avoid scanning all groups by reading the lifecycle index column families: only
 * {@code PREPARING_REBALANCE} groups (assignor) or {@code EMPTY} groups (retention) are returned.
 *
 * <p>Must be used from a single actor (a separate instance per reader actor); its column families
 * are opened lazily on first use so the handles and flyweights belong to that reader thread.
 */
public final class ConsumerGroupQueryService {

  private final ZeebeDb<EventBridgeColumnFamilies> zeebeDb;

  private DbString groupId;
  private DbString memberId;
  private DbCompositeKey<DbString, DbString> groupMemberKey;
  private ColumnFamily<DbString, GroupState> groupColumnFamily;
  private ColumnFamily<DbCompositeKey<DbString, DbString>, MemberState> memberColumnFamily;
  private ColumnFamily<DbString, DbNil> pendingColumnFamily;
  private ColumnFamily<DbString, DbNil> emptyColumnFamily;

  public ConsumerGroupQueryService(final ZeebeDb<EventBridgeColumnFamilies> zeebeDb) {
    this.zeebeDb = zeebeDb;
  }

  /** The group's snapshot (with its members), or {@code null} if the group does not exist. */
  public GroupSnapshot groupSnapshot(final String group) {
    ensureOpened();
    return readSnapshot(group);
  }

  /**
   * Snapshots of every group (a full scan — for admin reads / leader seeding, not the hot path).
   */
  public List<GroupSnapshot> allGroups() {
    ensureOpened();
    final var ids = new ArrayList<String>();
    groupColumnFamily.forEach((key, value) -> ids.add(key.toString()));
    return snapshots(ids);
  }

  /** Snapshots of the groups in {@code PREPARING_REBALANCE} (read from the index, no full scan). */
  public List<GroupSnapshot> pendingRebalanceGroups() {
    ensureOpened();
    return snapshots(indexedGroupIds(pendingColumnFamily));
  }

  /** Snapshots of the {@code EMPTY} groups (read from the index, no full scan). */
  public List<GroupSnapshot> emptyGroups() {
    ensureOpened();
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
    final var groupState = groupColumnFamily.get(groupId);
    if (groupState == null) {
      return null;
    }
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
        groupState.getGroupEpoch(),
        groupState.getAssignmentEpoch(),
        groupState.getState(),
        groupState.getSubscriptions(),
        Map.copyOf(members));
  }

  private void ensureOpened() {
    if (groupColumnFamily != null) {
      return;
    }
    final var context = zeebeDb.createContext();
    groupId = new DbString();
    memberId = new DbString();
    groupMemberKey = new DbCompositeKey<>(groupId, memberId);
    groupColumnFamily =
        zeebeDb.createColumnFamily(
            EventBridgeColumnFamilies.CONSUMER_GROUPS, context, groupId, new GroupState());
    memberColumnFamily =
        zeebeDb.createColumnFamily(
            EventBridgeColumnFamilies.CONSUMER_GROUP_MEMBERS,
            context,
            groupMemberKey,
            new MemberState());
    pendingColumnFamily =
        zeebeDb.createColumnFamily(
            EventBridgeColumnFamilies.CONSUMER_GROUPS_PENDING_REBALANCE,
            context,
            groupId,
            DbNil.INSTANCE);
    emptyColumnFamily =
        zeebeDb.createColumnFamily(
            EventBridgeColumnFamilies.CONSUMER_GROUPS_EMPTY, context, groupId, DbNil.INSTANCE);
  }
}
