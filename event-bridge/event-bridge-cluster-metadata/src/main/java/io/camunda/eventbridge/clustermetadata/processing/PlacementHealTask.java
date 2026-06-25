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
              final var target = heal(meta.assignment(), meta.replicationFactor(), active, live);
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
   * The minimal-diff healed assignment: per partition, keep the still-active committed replicas
   * (preserving order) and, if that leaves it below the replication factor, top up from active
   * brokers not already on the partition — deterministically, rotated by partition id so
   * replacements spread instead of piling onto the lowest id. Equal to {@code committed} when every
   * replica is already active (the task then does nothing).
   */
  private static Map<Integer, List<Integer>> heal(
      final Map<Integer, List<Integer>> committed,
      final int replicationFactor,
      final List<Integer> active,
      final Set<Integer> live) {
    final var healed = new LinkedHashMap<Integer, List<Integer>>();
    committed.forEach(
        (partition, replicas) -> {
          final var survivors = new ArrayList<Integer>();
          for (final var replica : replicas) {
            if (live.contains(replica)) {
              survivors.add(replica);
            }
          }
          if (survivors.size() < replicas.size() && survivors.size() < replicationFactor) {
            final var pool = new ArrayList<Integer>();
            for (final var broker : active) {
              if (!survivors.contains(broker)) {
                pool.add(broker);
              }
            }
            if (!pool.isEmpty()) {
              Collections.rotate(pool, -(partition % pool.size()));
              final var topUp = Math.min(replicationFactor - survivors.size(), pool.size());
              survivors.addAll(pool.subList(0, topUp));
            }
          }
          healed.put(partition, survivors);
        });
    return healed;
  }
}
