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
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * The async rebalance assignor — it computes each group's target assignment off the
 * command-processing path. It is registered as a {@link StreamProcessorLifecycleAware} listener and
 * self-schedules at a fixed rate on the async task group in {@link #onRecovered} — leader only, off
 * the command-processing path, and only after recovery (when the async task group is up), mirroring
 * the engine's {@code MessageTimeToLiveCheckScheduler}.
 *
 * <p>The task is <b>stateless</b>: the debounce lives in replicated state as each group's {@code
 * rebalanceDueAt} (set by the membership processors), keyed into the due-ordered {@code
 * CONSUMER_GROUPS_REBALANCE_DUE} index. On each tick it reads the groups whose deadline has passed
 * (ascending, via {@link ConsumerGroupState#rebalancesDueBy}), runs the {@link PartitionAssignor}
 * over each roster, and appends a {@code REBALANCE_GROUP} command carrying the proposed target. The
 * {@link RebalanceProcessor} then validates and commits it; a group leaves the index when {@code
 * GROUP_REBALANCED} applies. A re-proposal in the window before that lands is harmless — {@link
 * TransitionValidator#validateRebalance} drops a target whose epoch is already applied.
 *
 * <p>It reads state off-actor through its own {@link ConsumerGroupState} instance (a private
 * context) and emits commands only.
 */
public final class RebalanceAssignorTask implements Task, StreamProcessorLifecycleAware {

  private final Duration interval;
  private final ConsumerGroupState state;
  private final PartitionAssignor assignor;
  private final InstantSource clock;

  public RebalanceAssignorTask(
      final Duration interval,
      final ConsumerGroupState state,
      final PartitionAssignor assignor,
      final InstantSource clock) {
    this.interval = interval;
    this.state = state;
    this.assignor = assignor;
    this.clock = clock;
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    // The async task group is submitted at recovery; schedule the recurring scan now (leader only).
    context.getScheduleService().runAtFixedRateAsync(interval, this);
  }

  @Override
  public TaskResult execute(final TaskResultBuilder taskResultBuilder) {
    final var now = clock.instant().toEpochMilli();
    // The index returns only groups whose debounce deadline has passed, in due order — no full
    // scan,
    // no in-memory debounce.
    for (final var snapshot : state.rebalancesDueBy(now)) {
      taskResultBuilder.appendCommandRecord(CoordinatorIntent.REBALANCE_GROUP, propose(snapshot));
    }
    return taskResultBuilder.build();
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
}
