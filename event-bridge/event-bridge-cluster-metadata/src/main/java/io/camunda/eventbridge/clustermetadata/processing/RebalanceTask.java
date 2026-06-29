/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata.processing;

import io.camunda.eventbridge.clustermetadata.placement.RebalancePlanner;
import io.camunda.eventbridge.clustermetadata.record.TopicRecord;
import io.camunda.eventbridge.clustermetadata.state.immutable.BrokerState;
import io.camunda.eventbridge.clustermetadata.state.immutable.TopicState;
import io.camunda.eventbridge.clustermetadata.state.topic.TopicMetadata;
import io.camunda.eventbridge.clustermetadata.state.topic.TopicMetadata.TopicStatus;
import io.camunda.zeebe.protocol.record.intent.MetadataIntent;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.stream.api.scheduling.Task;
import io.camunda.zeebe.stream.api.scheduling.TaskResult;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The leader-only auto-rebalance controller: when the cluster has grown (or is otherwise uneven),
 * it nudges each topic's replica placement toward an even spread across the active brokers — but
 * <b>one move at a time</b>, never a big-bang whole-topic reassignment.
 *
 * <p>Registered as a {@link StreamProcessorLifecycleAware} and self-scheduling on the async task
 * group in {@link #onRecovered}, exactly like {@link PlacementHealTask} and {@link
 * BrokerEvictionTask}. On each tick it reads the replicated topic + broker registries through its
 * <em>own</em> private contexts and, only if the cluster is <b>quiescent</b> (no topic has an
 * in-flight reassignment target), asks {@link RebalancePlanner} for the single best replica
 * relocation of the first imbalanced topic. It appends one {@code REGISTER_TOPIC} command carrying
 * that one-partition target delta; the change-coordinator then drives it grow-first (passive-join →
 * promote → leave). The next move waits until that one converges (the target clears), so at most
 * one partition is ever in motion across the whole cluster.
 *
 * <p>Pacing this way — minimal-diff moves, one at a time, behind the quiescence gate — keeps a
 * partition from ever being fully swapped, so it always retains a caught-up quorum/log anchor
 * through the move. It defers to {@link PlacementHealTask}: while any replica sits on a non-active
 * broker the planner proposes nothing, so healing off failed brokers happens first.
 */
public final class RebalanceTask implements Task, StreamProcessorLifecycleAware {

  private static final Logger LOG = LoggerFactory.getLogger(RebalanceTask.class);

  private final Duration interval;
  private final TopicState topicState;
  private final BrokerState brokerState;
  private final int minImbalance;

  public RebalanceTask(
      final Duration interval,
      final TopicState topicState,
      final BrokerState brokerState,
      final int minImbalance) {
    this.interval = interval;
    this.topicState = topicState;
    this.brokerState = brokerState;
    this.minImbalance = minImbalance;
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    context.getScheduleService().runAtFixedRateAsync(interval, this);
  }

  @Override
  public TaskResult execute(final TaskResultBuilder taskResultBuilder) {
    final var active = brokerState.activeBrokers();
    if (active.size() < 2) {
      return taskResultBuilder.build();
    }
    final var topics = topicState.topicsSnapshot();
    // Quiescence gate: at most one move in flight across the whole cluster. While any topic has an
    // in-flight reassignment target, wait for it to converge before proposing the next move.
    if (topics.values().stream().anyMatch(TopicMetadata::hasTarget)) {
      return taskResultBuilder.build();
    }
    // Deterministic topic order so a re-derivation after a leader failover proposes the same move.
    for (final var entry : new TreeMap<>(topics).entrySet()) {
      final var name = entry.getKey();
      final var meta = entry.getValue();
      if (meta.status() != TopicStatus.ACTIVE) {
        continue;
      }
      final var move = RebalancePlanner.nextMove(meta.assignment(), active, minImbalance);
      if (move.isEmpty()) {
        continue;
      }
      final var target = new LinkedHashMap<>(meta.assignment());
      target.put(move.get().partitionId(), move.get().newReplicas());
      LOG.info(
          "Rebalancing topic {} partition {}: {} -> {} (active brokers {})",
          name,
          move.get().partitionId(),
          meta.assignment().get(move.get().partitionId()),
          move.get().newReplicas(),
          active);
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
      // One move per tick, cluster-wide — the quiescence gate above holds the next until this
      // lands.
      return taskResultBuilder.build();
    }
    return taskResultBuilder.build();
  }
}
