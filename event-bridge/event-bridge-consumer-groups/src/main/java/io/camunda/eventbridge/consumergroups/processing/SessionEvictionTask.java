/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.processing;

import io.camunda.eventbridge.consumergroups.record.MembershipRecord;
import io.camunda.eventbridge.consumergroups.session.GroupLiveness;
import io.camunda.eventbridge.consumergroups.session.MemberLivenessMirror;
import io.camunda.eventbridge.consumergroups.state.immutable.ConsumerGroupState;
import io.camunda.zeebe.protocol.record.intent.CoordinatorIntent;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.stream.api.scheduling.Task;
import io.camunda.zeebe.stream.api.scheduling.TaskResult;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * Evicts dead consumer sessions — the leader-only liveness sweep, registered as a {@link
 * StreamProcessorLifecycleAware} and self-scheduling on the async task group in {@link
 * #onRecovered}, exactly like {@link RebalanceAssignorTask}. On each tick it walks the off-actor
 * {@link MemberLivenessMirror} (its key set is the bounded work set — only groups with members
 * heartbeating), fetches each group's roster from its own {@link ConsumerGroupState} (a private
 * context), and appends a {@code LEAVE_GROUP} command for every member whose session lapsed — or,
 * when a rebalance has stalled, that never confirmed the target. The {@link LeaveGroupProcessor}
 * then removes the member and bumps the group epoch, identically to a voluntary leave.
 *
 * <p>It emits commands only. The fixed-rate re-run is its own retry — a member still expired next
 * tick is appended again, and a {@code LEAVE_GROUP} that lost a race is harmless (the processor
 * rejects an unknown member). Liveness is leader-local and not replicated, so it is cleared when
 * the node stops leading.
 */
public final class SessionEvictionTask implements Task, StreamProcessorLifecycleAware {

  private final Duration interval;
  private final Duration sessionTimeout;
  private final Duration rebalanceTimeout;
  private final ConsumerGroupState state;
  private final MemberLivenessMirror liveness;
  private final InstantSource clock;

  /**
   * When the sweep first observed a roster member with no liveness (keyed {@code group#member}) —
   * the grace baseline for evicting never-heartbeated members during a stalled rebalance.
   * Task-local like the sweep itself, and dropped with the other ephemeral state when the node
   * stops leading.
   */
  private final Map<String, Instant> unseenSince = new HashMap<>();

  /**
   * The last replicated memberEpoch observed for each roster member (keyed {@code group#member}). A
   * member's epoch only ever advances again, on an already-known memberId, via a static-membership
   * takeover — so an increase since the last tick means this tick's liveness entry (if any) still
   * belongs to the superseded incarnation. Excluding such an entry from {@link
   * GroupLiveness#membersToEvict} for this tick is what keeps a just-taken-over member from being
   * evicted for its predecessor's staleness (invariant 3): it is treated exactly like a freshly
   * joined member — unseen, subject to the same grace — instead of blamed for a heartbeat it never
   * sent. Task-local like {@link #unseenSince}; dropped together with the other ephemeral state
   * when the node stops leading.
   */
  private final Map<String, Long> lastObservedMemberEpoch = new HashMap<>();

  public SessionEvictionTask(
      final Duration interval,
      final Duration sessionTimeout,
      final Duration rebalanceTimeout,
      final ConsumerGroupState state,
      final MemberLivenessMirror liveness,
      final InstantSource clock) {
    this.interval = interval;
    this.sessionTimeout = sessionTimeout;
    this.rebalanceTimeout = rebalanceTimeout;
    this.state = state;
    this.liveness = liveness;
    this.clock = clock;
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    // The async task group is up at recovery; schedule the recurring sweep now (leader only).
    context.getScheduleService().runAtFixedRateAsync(interval, this);
  }

  @Override
  public void onClose() {
    liveness.clear();
    unseenSince.clear();
    lastObservedMemberEpoch.clear();
  }

  @Override
  public void onFailed() {
    liveness.clear();
    unseenSince.clear();
    lastObservedMemberEpoch.clear();
  }

  @Override
  public void onPaused() {
    liveness.clear();
    unseenSince.clear();
    lastObservedMemberEpoch.clear();
  }

  @Override
  public TaskResult execute(final TaskResultBuilder taskResultBuilder) {
    final var now = clock.instant();
    final var liveGroups = new HashSet<String>();
    final var currentlyUnseen = new HashSet<String>();
    final var currentMembers = new HashSet<String>();

    for (final var groupId : liveness.groupIds()) {
      final var group = state.groupSnapshot(groupId);
      if (group == null) {
        continue;
      }
      liveGroups.add(group.groupId());
      final var publishedLiveness = liveness.get(group.groupId());
      if (publishedLiveness == null) {
        continue;
      }

      // A static-membership takeover reuses the incumbent's memberId, so its liveness entry (if
      // any) still reflects the superseded incarnation until the successor's first heartbeat
      // overwrites it. Detect that by tracking each member's last-observed replicated epoch: an
      // increase since the previous tick can only be a takeover (nothing else ever advances an
      // existing member's epoch), so strip that entry from this tick's view — the loops below then
      // treat the member as unseen (freshly-joined grace), never as "went silent" (invariant 3).
      final var effectiveMembers = new HashMap<>(publishedLiveness.members());
      for (final var memberId : group.members().keySet()) {
        final var key = unseenKey(group.groupId(), memberId);
        currentMembers.add(key);
        final var currentEpoch = group.members().get(memberId).memberEpoch();
        final var previousEpoch = lastObservedMemberEpoch.put(key, currentEpoch);
        if (previousEpoch != null && previousEpoch < currentEpoch) {
          effectiveMembers.remove(memberId);
        }
      }
      final var groupLiveness =
          new GroupLiveness(publishedLiveness.rebalanceStartedAt(), effectiveMembers);

      // Record when a roster member without (current-incarnation) liveness was first observed —
      // the grace baseline for the stalled-rebalance eviction of members that never heartbeated on
      // this leader, including a just-taken-over member that has not yet heartbeated either.
      for (final var memberId : group.members().keySet()) {
        if (!groupLiveness.members().containsKey(memberId)) {
          final var key = unseenKey(group.groupId(), memberId);
          currentlyUnseen.add(key);
          unseenSince.putIfAbsent(key, now);
        }
      }
      for (final var memberId :
          groupLiveness.membersToEvict(
              group,
              now,
              sessionTimeout,
              rebalanceTimeout,
              memberId -> unseenSince.get(unseenKey(group.groupId(), memberId)))) {
        final var member = group.members().get(memberId);
        if (member == null) {
          continue;
        }
        taskResultBuilder.appendCommandRecord(
            CoordinatorIntent.LEAVE_GROUP,
            new MembershipRecord()
                .setGroupId(group.groupId())
                .setMemberId(memberId)
                .setMemberEpoch(member.memberEpoch()));
      }
    }

    liveness.retain(liveGroups);
    unseenSince.keySet().retainAll(currentlyUnseen);
    lastObservedMemberEpoch.keySet().retainAll(currentMembers);
    return taskResultBuilder.build();
  }

  private static String unseenKey(final String groupId, final String memberId) {
    return groupId + "#" + memberId;
  }
}
