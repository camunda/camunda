/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.processing;

import io.camunda.eventbridge.consumergroups.record.MembershipRecord;
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

/**
 * Reclaims empty consumer groups (and their committed offsets) after a retention window — the
 * event-bridge analog of Kafka's {@code offsets.retention}. Registered as a {@link
 * StreamProcessorLifecycleAware} listener that self-schedules at a fixed rate on the async task
 * group (leader only, off the processing path), mirroring {@link RebalanceAssignorTask}. On each
 * tick it reads the {@code EMPTY} groups from the {@link ConsumerGroupState} lifecycle index (no
 * full scan) and, for any whose replicated {@code emptySince} is older than the retention window,
 * appends a {@code DELETE_GROUP} command (the {@link DeleteGroupProcessor} validates and commits
 * it; {@code GroupDeletedApplier} removes the group + offsets).
 *
 * <p>The retention deadline lives in replicated state ({@code GroupState.emptySince}, stamped when
 * the group became empty), so it survives failover and the task itself is stateless — the scan
 * cadence only affects how promptly an expired group is reclaimed, never the deadline.
 */
public final class GroupRetentionTask implements Task, StreamProcessorLifecycleAware {

  private final Duration interval;
  private final Duration retention;
  private final ConsumerGroupState state;
  private final InstantSource clock;

  public GroupRetentionTask(
      final Duration interval,
      final Duration retention,
      final ConsumerGroupState state,
      final InstantSource clock) {
    this.interval = interval;
    this.retention = retention;
    this.state = state;
    this.clock = clock;
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    context.getScheduleService().runAtFixedRateAsync(interval, this);
  }

  @Override
  public TaskResult execute(final TaskResultBuilder taskResultBuilder) {
    final var now = clock.instant();
    // The index returns only EMPTY groups — no full scan.
    for (final var snapshot : state.emptyGroups()) {
      final var emptySince = Instant.ofEpochMilli(snapshot.emptySince());
      if (!Duration.between(emptySince, now).minus(retention).isNegative()) {
        taskResultBuilder.appendCommandRecord(
            CoordinatorIntent.DELETE_GROUP, new MembershipRecord().setGroupId(snapshot.groupId()));
      }
    }
    return taskResultBuilder.build();
  }
}
