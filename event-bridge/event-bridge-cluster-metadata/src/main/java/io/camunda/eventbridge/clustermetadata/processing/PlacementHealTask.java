/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata.processing;

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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Heals topic placement off brokers that are no longer placement-eligible (fenced or draining) —
 * the leader-only re-placement sweep, registered as a {@link StreamProcessorLifecycleAware} and
 * self-scheduling on the async task group in {@link #onRecovered}, exactly like {@link
 * BrokerEvictionTask}. On each tick it reads the replicated topic + broker registries through its
 * <em>own</em> private contexts (no flyweights shared with the processing actor), and for any
 * steady-state topic with a committed replica on a non-active broker it appends a {@code
 * REGISTER_TOPIC} command carrying a healed target. The change-coordinator (on the {@code
 * MetadataManager}) then drives committed → target one safe Raft step at a time.
 *
 * <p>The heal is <b>minimal-diff</b>: per partition it keeps the still-active committed replicas
 * and only replaces the non-active ones, topping up toward the replication factor from the active
 * set (deterministically, rotated by partition id for spread). It never moves data off a healthy
 * replica, and degrades gracefully when many brokers are down — it keeps whatever survives rather
 * than recomputing a fresh layout.
 *
 * <p>It is stateless and idempotent: it keys off the committed assignment, not broker state, so a
 * topic whose committed replicas are all active is skipped (no work), and a topic already targeting
 * (its heal in flight) is skipped. The fixed-rate re-run is its own retry. (A debounced,
 * due-index-driven schedule is a possible later optimization — see the broker-state-machine brief.)
 */
public final class PlacementHealTask implements Task, StreamProcessorLifecycleAware {

  private static final Logger LOG = LoggerFactory.getLogger(PlacementHealTask.class);

  private final Duration interval;
  private final TopicState topicState;
  private final BrokerState brokerState;

  public PlacementHealTask(
      final Duration interval, final TopicState topicState, final BrokerState brokerState) {
    this.interval = interval;
    this.topicState = topicState;
    this.brokerState = brokerState;
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
              final var target = heal(meta.assignment(), active, live, meta.replicationFactor());
              if (target.equals(meta.assignment())) {
                return;
              }
              LOG.info(
                  "Healing topic {} off non-active brokers (active={}); committed={} target={}",
                  name,
                  active,
                  meta.assignment(),
                  target);
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

  /**
   * The minimal-diff healed assignment: per partition, <b>replace each non-active replica with an
   * available spare</b> — an active broker not already on the partition — preserving the replica
   * count and order. Crucially, if no spare is available the non-active replica is <b>kept</b>, not
   * dropped: with no broker to take over there is nothing to gain by removing it, and leaving it in
   * the assignment lets it resume when it re-registers, or be taken over once a new broker appears.
   * Spares are picked deterministically, rotated by partition id so replacements spread across
   * brokers. Equal to {@code committed} when every replica is active and the partition is already
   * at its replication factor, or when the only non-active replicas have no spare to replace them
   * and no spare is available to top up (the task then does nothing).
   *
   * <p>It also <b>tops up an under-replicated partition</b> toward {@code replicationFactor} using
   * any remaining active spares — e.g. a topic created against a partial cluster (placed on fewer
   * brokers than RF), or one that lost replicas with no spare available at the time. These are pure
   * additions the change-coordinator grows in.
   */
  private static Map<Integer, List<Integer>> heal(
      final Map<Integer, List<Integer>> committed,
      final List<Integer> active,
      final Set<Integer> live,
      final int replicationFactor) {
    final var healed = new LinkedHashMap<Integer, List<Integer>>();
    committed.forEach(
        (partition, replicas) -> {
          final var spares = new ArrayList<Integer>();
          for (final var broker : active) {
            if (!replicas.contains(broker)) {
              spares.add(broker);
            }
          }
          if (!spares.isEmpty()) {
            Collections.rotate(spares, -(partition % spares.size()));
          }
          final var result = new ArrayList<Integer>(replicas.size());
          var nextSpare = 0;
          for (final var replica : replicas) {
            if (live.contains(replica)) {
              result.add(replica); // still active — keep it
            } else if (nextSpare < spares.size()) {
              result.add(spares.get(nextSpare++)); // fenced/draining — a spare takes over
            } else {
              result.add(replica); // no spare available — keep it in the topology
            }
          }
          // Top up toward the replication factor with any remaining active spares (adds only).
          while (result.size() < replicationFactor && nextSpare < spares.size()) {
            final var spare = spares.get(nextSpare++);
            if (!result.contains(spare)) {
              result.add(spare);
            }
          }
          healed.put(partition, result);
        });
    return healed;
  }
}
