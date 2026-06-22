/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.coordinator;

import static io.camunda.eventbridge.broker.coordinator.ConsumerGroup.ConsumerGroupState.EMPTY;
import static io.camunda.eventbridge.broker.coordinator.ConsumerGroup.ConsumerGroupState.REBALANCING;
import static io.camunda.eventbridge.broker.coordinator.ConsumerGroup.ConsumerGroupState.STABILIZED;
import static io.camunda.eventbridge.broker.coordinator.ConsumerGroup.ConsumerGroupState.STABILIZING;

import io.camunda.eventbridge.broker.coordinator.assignor.BalancedStickyAssignor;
import io.camunda.eventbridge.broker.coordinator.assignor.PartitionAssignment;
import io.camunda.eventbridge.broker.coordinator.assignor.PartitionAssignment.ReconciliationResult;
import io.camunda.eventbridge.broker.coordinator.assignor.PartitionAssignor;
import io.camunda.eventbridge.broker.coordinator.assignor.PartitionAssignor.PartitionAssignmentContext;
import io.camunda.eventbridge.broker.coordinator.stream.GroupMetadataCodec.GroupMetadata;
import io.camunda.eventbridge.broker.coordinator.stream.GroupMetadataCodec.MemberSnapshot;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.IntStream;

public final class ConsumerGroup {

  private static final Map<ConsumerGroupState, Set<ConsumerGroupState>> VALID_TRANSITIONS =
      Map.of(
          EMPTY, Set.of(REBALANCING),
          REBALANCING, Set.of(STABILIZING, EMPTY),
          STABILIZING, Set.of(STABILIZED, REBALANCING, EMPTY),
          STABILIZED, Set.of(REBALANCING, EMPTY));

  private final String groupId;
  private final int partitionCount;
  private final ConcurrencyControl executor;
  private final InstantSource clock;

  private ConsumerGroupState state;
  private int assignmentEpoch;
  private final Duration debounce;
  private final Duration rebalanceTimeout;
  private Instant rebalanceStartedAt;

  private final Map<String, GroupMemberSession> sessions = new HashMap<>();
  private final Map<String, MemberMetadata> knownStaticMembers = new HashMap<>();
  private final Set<String> stableConsumers = new HashSet<>();

  private final Set<Integer> pendingRevocations = new HashSet<>();

  private final PartitionAssignor assignor;
  private final List<Integer> partitions;
  private PartitionAssignment partitionAssignment;

  // Invoked after each rebalance so the coordinator can replicate the new membership/assignment.
  private final Consumer<ConsumerGroup> onRebalanced;

  ConsumerGroup(
      final String groupId,
      final int partitionCount,
      final ConcurrencyControl executor,
      final InstantSource clock,
      final Consumer<ConsumerGroup> onRebalanced) {
    this.groupId = groupId;
    this.partitionCount = partitionCount;
    this.executor = executor;
    this.clock = clock;
    this.onRebalanced = onRebalanced;
    state = EMPTY;
    assignmentEpoch = 1;
    debounce = Duration.ofSeconds(2);
    rebalanceTimeout = Duration.ofSeconds(30);
    assignor = new BalancedStickyAssignor();
    partitionAssignment = new PartitionAssignment(Map.of());
    partitions = createPartitions(partitionCount);
  }

  public GroupMemberSession getSession(final String memberId) {
    return sessions.get(memberId);
  }

  public void addSession(final GroupMemberSession session) {
    final var metadata = session.getMetadata();
    final var memberId = metadata.getMemberId();
    final var instanceId = metadata.getInstanceId();

    sessions.put(memberId, session);

    if (metadata.isStaticMember()) {
      knownStaticMembers.put(instanceId, metadata);
    }

    scheduleRebalance();
  }

  public void removeSession(final String memberId) {
    sessions.remove(memberId);
    stableConsumers.remove(memberId);

    if (sessions.isEmpty()) {
      transitionTo(EMPTY);
      return;
    }

    scheduleRebalance();
  }

  private void scheduleRebalance() {
    if (state != REBALANCING) {
      transitionTo(REBALANCING);
      executor.schedule(debounce, this::rebalance);
    }
  }

  private void transitionTo(final ConsumerGroupState target) {
    if (state != target && VALID_TRANSITIONS.get(state).contains(target)) {
      state = target;
    }
  }

  public String getGroupId() {
    return groupId;
  }

  /** Returns the current rebalance epoch. */
  public long getAssignmentEpoch() {
    return assignmentEpoch;
  }

  public boolean isActiveConsumer(final String memberId) {
    return sessions.containsKey(memberId);
  }

  /**
   * Whether the coordinator currently designates {@code memberId} as the owner of {@code
   * partitionId} — true if the partition is in the member's confirmed assignment or its current
   * target assignment. Used to fence offset commits. Checking the target (not only the confirmed
   * handshake result) lets a freshly assigned member commit during the brief window before the
   * rebalance stabilizes; stale-generation zombies are already rejected by the member-epoch check
   * that runs before this one.
   */
  public boolean isAssignedPartition(final String memberId, final int partitionId) {
    final var session = sessions.get(memberId);
    if (session != null && session.getAssignedPartitions().contains(partitionId)) {
      return true;
    }
    return partitionAssignment.forConsumer(memberId).contains(partitionId);
  }

  List<GroupMemberSession> getExpiredSessions(final Instant deadline) {
    return sessions.values().stream()
        .filter(session -> session.isSessionExpired(deadline))
        .toList();
  }

  List<GroupMemberSession> getNonConvergedSessions() {
    return sessions.values().stream()
        .filter(session -> session.getAssignmentEpoch() != assignmentEpoch)
        .toList();
  }

  private void rebalance() {
    final var activeConsumers = new ArrayList<>(sessions.keySet());
    final var context =
        new PartitionAssignmentContext(activeConsumers, partitionAssignment, partitions);
    partitionAssignment = assignor.assign(context);
    assignmentEpoch++;
    stableConsumers.clear();
    pendingRevocations.clear();
    computePendingRevocations();
    rebalanceStartedAt = clock.instant();
    transitionTo(STABILIZING);

    // Replicate the new membership/assignment so it survives coordinator failover.
    onRebalanced.accept(this);
  }

  /** Builds a replicable snapshot of the current membership and target assignment. */
  GroupMetadata toMetadata() {
    final List<MemberSnapshot> members = new ArrayList<>();
    for (final var session : sessions.values()) {
      final var metadata = session.getMetadata();
      members.add(
          new MemberSnapshot(
              metadata.getMemberId(),
              metadata.getInstanceId(),
              metadata.getMemberEpoch(),
              new ArrayList<>(partitionAssignment.forConsumer(metadata.getMemberId()))));
    }
    return new GroupMetadata(assignmentEpoch, members);
  }

  /**
   * Rebuilds the group from replicated metadata after a coordinator failover: restores members,
   * their epochs, and confirmed assignments into a STABILIZED group, with fresh heartbeat deadlines
   * so re-attaching consumers are not immediately evicted.
   */
  void restore(final GroupMetadata metadata, final Instant now) {
    assignmentEpoch = metadata.assignmentEpoch();
    final Map<String, List<Integer>> assignments = new HashMap<>();
    for (final var member : metadata.members()) {
      final var memberMetadata =
          member.instanceId() == null
              ? MemberMetadata.dynamicMember(member.memberId())
              : MemberMetadata.staticMember(member.memberId(), member.instanceId());
      memberMetadata.setMemberEpoch((int) member.memberEpoch());

      final var session = new GroupMemberSession(memberMetadata, now);
      session.confirmAssignment(assignmentEpoch, member.partitions());
      sessions.put(member.memberId(), session);
      stableConsumers.add(member.memberId());
      if (memberMetadata.isStaticMember()) {
        knownStaticMembers.put(memberMetadata.getInstanceId(), memberMetadata);
      }
      assignments.put(member.memberId(), member.partitions());
    }
    partitionAssignment = new PartitionAssignment(assignments);
    state = sessions.isEmpty() ? EMPTY : STABILIZED;
  }

  /**
   * Computes the set of partitions that need to move between consumers. These partitions must not
   * be assigned to the new owner until the previous owner confirms revocation.
   */
  private void computePendingRevocations() {
    for (final var session : sessions.values()) {
      final var memberId = session.getMetadata().getMemberId();
      final var owned = session.getAssignedPartitions();
      final var target = partitionAssignment.forConsumer(memberId);

      for (final var partition : owned) {
        if (!target.contains(partition)) {
          pendingRevocations.add(partition);
        }
      }
    }
  }

  public ReconciliationResult reconcileAssignment(
      final GroupMemberSession session, final List<Integer> ownedPartitions) {
    if (state != STABILIZING) {
      // Not rebalancing: drive the member back to its confirmed assignment. In steady state this is
      // a no-op (owned == confirmed), but after a rejoin (which clears the client's owned set) or a
      // failover re-attach it re-sends the confirmed partitions so the member recovers them instead
      // of being stuck owning nothing.
      return restoreToConfirmedAssignment(session, ownedPartitions);
    }

    final var memberId = session.getMetadata().getMemberId();
    final var ownedSet = new HashSet<>(ownedPartitions);
    final var delta = partitionAssignment.computeDelta(memberId, ownedPartitions, ownedSet);

    // Detect confirmed revocations — partitions in last confirmed assignment
    // that the consumer no longer reports owning
    for (final var partition : session.getAssignedPartitions()) {
      if (!ownedSet.contains(partition)) {
        pendingRevocations.remove(partition);
      }
    }

    // Withhold assignments still pending revocation elsewhere
    final var safeAssign =
        delta.assign().stream().filter(p -> !pendingRevocations.contains(p)).toList();

    final var safeDelta = new ReconciliationResult(delta.revoke(), safeAssign, delta.assignment());

    if (delta.assign().isEmpty() && delta.revoke().isEmpty()) {
      session.confirmAssignment(assignmentEpoch, ownedPartitions);
      trackStability(memberId);
    }

    return safeDelta;
  }

  /**
   * Computes the delta that moves a member from the partitions it currently reports owning to its
   * last confirmed assignment. Used outside an active rebalance to recover a member that rejoined
   * or re-attached after a coordinator failover (and therefore reports owning nothing).
   */
  private ReconciliationResult restoreToConfirmedAssignment(
      final GroupMemberSession session, final List<Integer> ownedPartitions) {
    final var confirmed = session.getAssignedPartitions();
    final var ownedSet = new HashSet<>(ownedPartitions);
    final var assign = confirmed.stream().filter(p -> !ownedSet.contains(p)).sorted().toList();
    final var revoke =
        ownedPartitions.stream().filter(p -> !confirmed.contains(p)).sorted().toList();
    return new ReconciliationResult(revoke, assign, List.copyOf(confirmed));
  }

  private void trackStability(final String memberId) {
    stableConsumers.add(memberId);
    if (stableConsumers.size() == sessions.size()) {
      transitionTo(STABILIZED);
    }
  }

  boolean isRebalanceTimedOut(final Instant now) {
    return state == STABILIZING
        && rebalanceStartedAt != null
        && Duration.between(rebalanceStartedAt, now).compareTo(rebalanceTimeout) > 0;
  }

  public int getPartitionCount() {
    return partitionCount;
  }

  public MemberMetadata getMemberByInstanceId(final String instanceId) {
    return knownStaticMembers.get(instanceId);
  }

  public boolean isRebalancing() {
    return state == REBALANCING || state == STABILIZING;
  }

  private static List<Integer> createPartitions(final int partitionCount) {
    // Partition IDs are 1-based across the system (broker partitions, publish/fetch/poll topics),
    // so the coordinator must assign 1-based IDs too — otherwise consumers poll a partition that
    // has no handler (e.g. partition 0).
    return IntStream.rangeClosed(1, partitionCount).boxed().toList();
  }

  enum ConsumerGroupState {
    EMPTY,
    REBALANCING,
    STABILIZING,
    STABILIZED;
  }
}
