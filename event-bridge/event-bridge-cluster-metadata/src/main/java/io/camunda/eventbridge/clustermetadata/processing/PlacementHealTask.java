/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata.processing;

import io.camunda.eventbridge.clustermetadata.placement.PlacementStrategy;
import io.camunda.eventbridge.clustermetadata.record.TopicRecord;
import io.camunda.eventbridge.clustermetadata.state.immutable.BrokerState;
import io.camunda.eventbridge.clustermetadata.state.immutable.TopicState;
import io.camunda.eventbridge.clustermetadata.state.topic.TopicMetadata.TopicStatus;
import io.camunda.zeebe.protocol.record.intent.MetadataIntent;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.stream.api.scheduling.Task;
import io.camunda.zeebe.stream.api.scheduling.TaskResult;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * Heals topic placement off brokers that are no longer placement-eligible (fenced or draining) —
 * the leader-only re-placement sweep, registered as a {@link StreamProcessorLifecycleAware} and
 * self-scheduling on the async task group in {@link #onRecovered}, exactly like {@link
 * BrokerEvictionTask}. On each tick it reads the replicated topic + broker registries through its
 * <em>own</em> private contexts (no flyweights shared with the processing actor), and for any
 * steady-state topic whose committed assignment references a non-active broker it appends a {@code
 * REGISTER_TOPIC} command carrying a fresh target over the active brokers. The change-coordinator
 * (on the {@code MetadataManager}) then drives committed → target one safe Raft step at a time.
 *
 * <p>It emits commands only; a topic already targeting (its heal in flight) is skipped, and the
 * fixed-rate re-run is its own retry. (A debounced, due-index-driven schedule is a later
 * optimization — see the broker-state-machine design brief.)
 */
public final class PlacementHealTask implements Task, StreamProcessorLifecycleAware {

  private final Duration interval;
  private final TopicState topicState;
  private final BrokerState brokerState;
  private final PlacementStrategy placement;

  public PlacementHealTask(
      final Duration interval,
      final TopicState topicState,
      final BrokerState brokerState,
      final PlacementStrategy placement) {
    this.interval = interval;
    this.topicState = topicState;
    this.brokerState = brokerState;
    this.placement = placement;
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    context.getScheduleService().runAtFixedRateAsync(interval, this);
  }

  @Override
  public TaskResult execute(final TaskResultBuilder taskResultBuilder) {
    final var active = brokerState.activeBrokers();
    if (active.isEmpty()) {
      return taskResultBuilder.build();
    }
    final var live = Set.copyOf(active);
    topicState
        .topicsSnapshot()
        .forEach(
            (name, meta) -> {
              if (meta.status() != TopicStatus.ACTIVE || meta.hasTarget()) {
                return;
              }
              final var onDeadBroker =
                  meta.assignment().values().stream()
                      .flatMap(List::stream)
                      .anyMatch(broker -> !live.contains(broker));
              if (!onDeadBroker) {
                return;
              }
              final var target =
                  placement.assign(meta.partitionCount(), meta.replicationFactor(), active);
              if (target.isEmpty() || target.equals(meta.assignment())) {
                return;
              }
              taskResultBuilder.appendCommandRecord(
                  MetadataIntent.REGISTER_TOPIC,
                  new TopicRecord()
                      .setName(name)
                      .setOp(TopicRecord.OP_REGISTER)
                      .setPartitionCount(meta.partitionCount())
                      .setReplicationFactor(meta.replicationFactor())
                      .setStatus(meta.status())
                      .setAssignment(meta.assignment())
                      .setTarget(target));
            });
    return taskResultBuilder.build();
  }
}
