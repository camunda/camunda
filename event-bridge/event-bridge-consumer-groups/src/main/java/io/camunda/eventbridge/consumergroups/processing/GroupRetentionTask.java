/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.processing;

import io.camunda.eventbridge.consumergroups.record.MembershipRecord;
import io.camunda.eventbridge.consumergroups.state.group.ConsumerGroupQueryService;
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
import java.util.Map;

/**
 * Reclaims empty consumer groups (and their committed offsets) after a retention window — the
 * event-bridge analog of Kafka's {@code offsets.retention}. Registered as a {@link
 * StreamProcessorLifecycleAware} listener that self-schedules at a fixed rate on the async task
 * group (leader only, off the processing path), mirroring {@link RebalanceAssignorTask}. On each
 * tick it reads the {@code EMPTY} groups from the {@link ConsumerGroupQueryService} lifecycle index
 * (no full scan) and, once one has been empty for at least the retention duration, appends a {@code
 * DELETE_GROUP} command (the {@link DeleteGroupProcessor} validates and commits it; {@code
 * GroupDeletedApplier} removes the group + offsets).
 *
 * <p>The "first seen empty" timestamps are kept in memory (leader-only); a failover resets them, so
 * retention is approximate (a failover can extend it) — acceptable, as in Kafka.
 */
public final class GroupRetentionTask implements Task, StreamProcessorLifecycleAware {

  private final Duration interval;
  private final Duration retention;
  private final ConsumerGroupQueryService query;
  private final InstantSource clock;

  // groupId -> when it was first observed EMPTY (leader-only, ephemeral).
  private final Map<String, Instant> emptySince = new HashMap<>();

  public GroupRetentionTask(
      final Duration interval,
      final Duration retention,
      final ConsumerGroupQueryService query,
      final InstantSource clock) {
    this.interval = interval;
    this.retention = retention;
    this.query = query;
    this.clock = clock;
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    context.getScheduleService().runAtFixedRateAsync(interval, this);
  }

  @Override
  public TaskResult execute(final TaskResultBuilder taskResultBuilder) {
    final var now = clock.instant();
    final var live = new java.util.HashSet<String>();
    // The index returns only EMPTY groups — no full scan.
    for (final var snapshot : query.emptyGroups()) {
      final var groupId = snapshot.groupId();
      live.add(groupId);
      final var since = emptySince.computeIfAbsent(groupId, ignored -> now);
      if (!Duration.between(since, now).minus(retention).isNegative()) {
        taskResultBuilder.appendCommandRecord(
            CoordinatorIntent.DELETE_GROUP, new MembershipRecord().setGroupId(groupId));
      }
    }
    // Forget groups that are no longer empty (a member rejoined) so their timer restarts next time.
    emptySince.keySet().retainAll(live);
    return taskResultBuilder.build();
  }
}
