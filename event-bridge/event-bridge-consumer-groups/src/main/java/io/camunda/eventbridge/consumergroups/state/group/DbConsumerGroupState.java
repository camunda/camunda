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
import io.camunda.zeebe.db.impl.DbString;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * RocksDB-backed {@link MutableConsumerGroupState}: per-group epochs/partition-count keyed by
 * {@code groupId} and per-member identity/epoch/target keyed by {@code (groupId, memberId)}.
 * Rebuilt identically on every replica via stream replay, so a new coordinator leader restores
 * membership after failover.
 *
 * <p>This class only does granular storage — get/put/delete plus thread-safe mirror upkeep. The
 * decision logic (create-the-group-if-absent, bump epochs, delete-when-empty) lives in the
 * appliers, mirroring how the engine keeps such logic out of its {@code Db…State} classes.
 *
 * <p>RocksDB reads ({@link #getGroup}, {@link #getMember}, {@link #findMemberByInstanceId}, {@link
 * #isGroupEmpty}) run on the stream-processing actor only; the mirror reads ({@link
 * #groupSnapshot}, {@link #groupSnapshots}) are safe off-actor (the heartbeat handler and the async
 * assignor).
 */
public final class DbConsumerGroupState implements MutableConsumerGroupState {

  private final DbString groupId = new DbString();
  private final DbString memberId = new DbString();
  private final DbCompositeKey<DbString, DbString> groupMemberKey =
      new DbCompositeKey<>(groupId, memberId);

  private final ColumnFamily<DbString, GroupState> groupColumnFamily;
  private final ColumnFamily<DbCompositeKey<DbString, DbString>, MemberState> memberColumnFamily;

  // Thread-safe mirror of the durable state, read off-actor by the heartbeat handler and assignor.
  private final Map<String, GroupSnapshot> mirror = new ConcurrentHashMap<>();

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

  // --- mirror reads (off-actor) -----------------------------------------------------------------

  @Override
  public GroupSnapshot groupSnapshot(final String group) {
    return mirror.get(group);
  }

  @Override
  public List<GroupSnapshot> groupSnapshots() {
    return new ArrayList<>(mirror.values());
  }

  // --- writes (appliers, stream-processing actor) -----------------------------------------------

  @Override
  public void putGroup(final String group, final GroupState value) {
    groupId.wrapString(group);
    groupColumnFamily.upsert(groupId, value);
    refreshMirror(group);
  }

  @Override
  public void deleteGroup(final String group) {
    groupId.wrapString(group);
    groupColumnFamily.deleteIfExists(groupId);
    mirror.remove(group);
  }

  @Override
  public void putMember(final String group, final String member, final MemberState value) {
    groupId.wrapString(group);
    memberId.wrapString(member);
    memberColumnFamily.upsert(groupMemberKey, value);
    refreshMirror(group);
  }

  @Override
  public void deleteMember(final String group, final String member) {
    groupId.wrapString(group);
    memberId.wrapString(member);
    memberColumnFamily.deleteIfExists(groupMemberKey);
    refreshMirror(group);
  }

  @Override
  public void seedMirror() {
    mirror.clear();
    final List<String> groups = new ArrayList<>();
    groupColumnFamily.forEach((key, value) -> groups.add(key.toString()));
    groups.forEach(this::refreshMirror);
  }

  // --- internals --------------------------------------------------------------------------------

  private void refreshMirror(final String group) {
    groupId.wrapString(group);
    final var groupState = groupColumnFamily.get(groupId);
    if (groupState == null) {
      mirror.remove(group);
      return;
    }
    final long groupEpoch = groupState.getGroupEpoch();
    final long assignmentEpoch = groupState.getAssignmentEpoch();
    final var lifecycle = groupState.getState();
    final var subscriptions = groupState.getSubscriptions();

    final Map<String, MemberSnapshot> members = new LinkedHashMap<>();
    groupId.wrapString(group);
    // Explicit BiConsumer type to disambiguate from the KeyValuePairVisitor overload.
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

    mirror.put(
        group,
        new GroupSnapshot(
            group, groupEpoch, assignmentEpoch, lifecycle, subscriptions, Map.copyOf(members)));
  }
}
