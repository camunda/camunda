/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.processing;

import io.camunda.eventbridge.consumergroups.assignor.PartitionAssignment;
import io.camunda.eventbridge.consumergroups.assignor.PartitionAssignor;
import io.camunda.eventbridge.consumergroups.assignor.PartitionAssignor.PartitionAssignmentContext;
import io.camunda.eventbridge.consumergroups.record.RebalanceRecord;
import io.camunda.eventbridge.consumergroups.state.group.GroupSnapshot;
import io.camunda.eventbridge.consumergroups.state.immutable.ConsumerGroupState;
import io.camunda.eventbridge.protocol.topic.TopicPartition;
import io.camunda.zeebe.protocol.record.intent.CoordinatorIntent;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.stream.api.scheduling.Task;
import io.camunda.zeebe.stream.api.scheduling.TaskResult;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * The async rebalance assignor — the server-side counterpart of Kafka KIP-848's target-assignment
 * computation. It is registered as a {@link StreamProcessorLifecycleAware} listener and
 * self-schedules at a fixed rate on the async task group in {@link #onRecovered} — leader only, off
 * the command-processing path, and only after recovery (when the async task group is up), mirroring
 * the engine's {@code MessageTimeToLiveCheckScheduler}. On each tick it reads the {@code
 * PREPARING_REBALANCE} groups from the {@link ConsumerGroupState} lifecycle index (no full scan),
 * runs the {@link PartitionAssignor} over each roster, and appends a {@code REBALANCE_GROUP}
 * command carrying the proposed target. The {@link RebalanceProcessor} then validates and commits
 * it.
 *
 * <p>It reads state off-actor through its own {@link ConsumerGroupState} instance (a private
 * context) and emits commands only.
 *
 * <p>Bursts of joins/leaves are debounced: a group's target is proposed only once its group epoch
 * has been stable for at least one tick, and only once per epoch (re-proposing is suppressed until
 * the previous proposal is applied). Stale proposals are harmless — {@link RebalanceProcessor}
 * drops any whose epoch no longer matches.
 */
public final class RebalanceAssignorTask implements Task, StreamProcessorLifecycleAware {

  private final Duration interval;
  private final ConsumerGroupState state;
  private final PartitionAssignor assignor;

  // Per-group debounce: the group epoch last observed and the last epoch a proposal was emitted
  // for.
  private final Map<String, Debounce> debounce = new HashMap<>();

  public RebalanceAssignorTask(
      final Duration interval, final ConsumerGroupState state, final PartitionAssignor assignor) {
    this.interval = interval;
    this.state = state;
    this.assignor = assignor;
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    // The async task group is submitted at recovery; schedule the recurring scan now (leader only).
    context.getScheduleService().runAtFixedRateAsync(interval, this);
  }

  @Override
  public TaskResult execute(final TaskResultBuilder taskResultBuilder) {
    // The index returns only PREPARING_REBALANCE groups (have members + a stale target), so there
    // is no full scan; drop debounce state for groups that are no longer pending.
    final var pending = state.pendingRebalanceGroups();
    final var pendingIds = new HashSet<String>();
    for (final var snapshot : pending) {
      pendingIds.add(snapshot.groupId());
      maybeProposeRebalance(snapshot, taskResultBuilder);
    }
    debounce.keySet().retainAll(pendingIds);
    return taskResultBuilder.build();
  }

  private void maybeProposeRebalance(
      final GroupSnapshot snapshot, final TaskResultBuilder taskResultBuilder) {
    final var groupId = snapshot.groupId();
    final var groupEpoch = snapshot.groupEpoch();
    final var d = debounce.get(groupId);
    if (d == null) {
      // First time we see this group pending — observe the epoch and wait one tick before acting.
      debounce.put(groupId, new Debounce(groupEpoch, -1L));
      return;
    }
    if (d.seenEpoch != groupEpoch) {
      // The roster changed since the last tick — restart the debounce window.
      d.seenEpoch = groupEpoch;
      d.emittedEpoch = -1L;
      return;
    }
    if (d.emittedEpoch == groupEpoch) {
      return; // already proposed for this epoch; awaiting the processor
    }

    taskResultBuilder.appendCommandRecord(CoordinatorIntent.REBALANCE_GROUP, propose(snapshot));
    d.emittedEpoch = groupEpoch;
  }

  private RebalanceRecord propose(final GroupSnapshot snapshot) {
    final var consumers = new ArrayList<>(snapshot.members().keySet());
    final Map<String, List<TopicPartition>> previous = new HashMap<>();
    snapshot
        .members()
        .forEach((memberId, member) -> previous.put(memberId, member.targetPartitions()));

    // The partition space is every (topic, partition) across all the group's subscribed topics.
    final List<TopicPartition> partitions = new ArrayList<>();
    snapshot
        .subscriptions()
        .forEach(
            (topic, count) ->
                IntStream.rangeClosed(1, count)
                    .forEach(partition -> partitions.add(new TopicPartition(topic, partition))));

    final var assignment =
        assignor.assign(
            new PartitionAssignmentContext(
                consumers, new PartitionAssignment(previous), partitions));

    return new RebalanceRecord()
        .setGroupId(snapshot.groupId())
        .setAssignmentEpoch(snapshot.groupEpoch())
        .setMembers(assignment.assignments());
  }

  private static final class Debounce {
    private long seenEpoch;
    private long emittedEpoch;

    private Debounce(final long seenEpoch, final long emittedEpoch) {
      this.seenEpoch = seenEpoch;
      this.emittedEpoch = emittedEpoch;
    }
  }
}
