/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.processing;

import io.camunda.eventbridge.consumergroups.record.MembershipRecord;
import io.camunda.eventbridge.consumergroups.session.MemberLivenessMirror;
import io.camunda.eventbridge.consumergroups.state.group.ConsumerGroupQueryService;
import io.camunda.zeebe.protocol.record.intent.CoordinatorIntent;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.stream.api.scheduling.Task;
import io.camunda.zeebe.stream.api.scheduling.TaskResult;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import java.time.Duration;
import java.time.InstantSource;
import java.util.HashSet;

/**
 * Evicts dead consumer sessions — the leader-only liveness sweep, registered as a {@link
 * StreamProcessorLifecycleAware} and self-scheduling on the async task group in {@link
 * #onRecovered}, exactly like {@link RebalanceAssignorTask}. On each tick it walks the off-actor
 * {@link MemberLivenessMirror} (its key set is the bounded work set — only groups with members
 * heartbeating), fetches each group's roster from the {@link ConsumerGroupQueryService}, and
 * appends a {@code LEAVE_GROUP} command for every member whose session lapsed — or, when a
 * rebalance has stalled, that never confirmed the target. The {@link LeaveGroupProcessor} then
 * removes the member and bumps the group epoch, identically to a voluntary leave.
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
  private final ConsumerGroupQueryService query;
  private final MemberLivenessMirror liveness;
  private final InstantSource clock;

  public SessionEvictionTask(
      final Duration interval,
      final Duration sessionTimeout,
      final Duration rebalanceTimeout,
      final ConsumerGroupQueryService query,
      final MemberLivenessMirror liveness,
      final InstantSource clock) {
    this.interval = interval;
    this.sessionTimeout = sessionTimeout;
    this.rebalanceTimeout = rebalanceTimeout;
    this.query = query;
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
  }

  @Override
  public void onFailed() {
    liveness.clear();
  }

  @Override
  public void onPaused() {
    liveness.clear();
  }

  @Override
  public TaskResult execute(final TaskResultBuilder taskResultBuilder) {
    final var now = clock.instant();
    final var liveGroups = new HashSet<String>();

    for (final var groupId : liveness.groupIds()) {
      final var group = query.groupSnapshot(groupId);
      if (group == null) {
        continue;
      }
      liveGroups.add(group.groupId());
      final var groupLiveness = liveness.get(group.groupId());
      if (groupLiveness == null) {
        continue;
      }
      for (final var memberId :
          groupLiveness.membersToEvict(group, now, sessionTimeout, rebalanceTimeout)) {
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
    return taskResultBuilder.build();
  }
}
