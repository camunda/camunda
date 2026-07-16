/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.bootstrap;

import io.camunda.eventbridge.broker.partitioning.PartitionFactory.CreatedPartition;
import io.camunda.eventbridge.broker.partitioning.PartitionLifecycle;
import io.camunda.zeebe.scheduler.Actor;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The inventory of partition replicas this broker hosts — data, coordinator, and metadata — and the
 * single owner of their teardown. Data replicas are additionally addressable by their (group,
 * partition) key for runtime reconfiguration (join/leave/promote/remove); coordinator and metadata
 * replicas are tracked for shutdown only.
 *
 * <p>Centralizing the bookkeeping here replaces the several parallel lists plus a map that
 * previously had to be kept in sync by hand at every add/remove/rollback site.
 */
final class PartitionRegistry {

  private static final Logger LOG = LoggerFactory.getLogger(PartitionRegistry.class);
  private static final int CLOSE_TIMEOUT_SECONDS = 30;

  /**
   * A provisioned data-partition replica, addressable by its (group, partition) key. {@code
   * compactor} is either a {@code LogRetentionCompactor} (DELETE policy) or a {@code LogCleaner}
   * (COMPACT policy) — the two are mutually exclusive by policy — or {@code null} if retention
   * compaction is disabled.
   */
  record DataReplica(CreatedPartition created, PartitionLifecycle lifecycle, Actor compactor) {}

  // CopyOnWrite/Concurrent: boot mutates these on the start thread, runtime topic provisioning on
  // the reconciler thread, and closeAll() iterates them — so all access must be thread-safe.
  private final List<CreatedPartition> rafts = new CopyOnWriteArrayList<>();
  private final List<Actor> components = new CopyOnWriteArrayList<>();
  private final Map<String, DataReplica> dataReplicas = new ConcurrentHashMap<>();

  private static String key(final String groupName, final int partitionId) {
    return groupName + "#" + partitionId;
  }

  /** Registers a coordinator/metadata replica: its Raft partition and its managing actor. */
  void addAuxiliary(final CreatedPartition created, final Actor component) {
    rafts.add(created);
    components.add(component);
  }

  /**
   * Registers a data-partition replica, addressable by (group, partition). A {@code null} compactor
   * (retention disabled) is allowed.
   */
  void addData(
      final String groupName,
      final int partitionId,
      final CreatedPartition created,
      final PartitionLifecycle lifecycle,
      final Actor compactor) {
    rafts.add(created);
    components.add(lifecycle);
    if (compactor != null) {
      components.add(compactor);
    }
    dataReplicas.put(key(groupName, partitionId), new DataReplica(created, lifecycle, compactor));
  }

  /** Whether a data replica of {@code (groupName, partitionId)} is currently tracked. */
  boolean containsData(final String groupName, final int partitionId) {
    return dataReplicas.containsKey(key(groupName, partitionId));
  }

  /** The tracked data replica of {@code (groupName, partitionId)}, or {@code null} if none. */
  DataReplica data(final String groupName, final int partitionId) {
    return dataReplicas.get(key(groupName, partitionId));
  }

  /**
   * Removes a data replica from all tracking and closes its actor components (lifecycle + retention
   * compactor), returning it so the caller can decide the Raft server's fate (leave the group vs.
   * abandon a failed start). The Raft partition and snapshot store are <b>not</b> closed here.
   * Returns {@code null} if no such replica is tracked.
   */
  DataReplica removeData(final String groupName, final int partitionId) {
    final var replica = dataReplicas.remove(key(groupName, partitionId));
    if (replica == null) {
      return null;
    }
    rafts.remove(replica.created());
    components.remove(replica.lifecycle());
    closeQuietly(replica.lifecycle(), "lifecycle for " + groupName + "/" + partitionId);
    if (replica.compactor() != null) {
      components.remove(replica.compactor());
      closeQuietly(replica.compactor(), "retention compactor for " + groupName + "/" + partitionId);
    }
    return replica;
  }

  /**
   * Two-phase teardown of every tracked replica: first close all managing actors (lifecycles,
   * retention compactors, coordinator/metadata partitions), then close all Raft partitions and
   * their snapshot stores. Actors are closed before Raft because they hold handlers/streams bound
   * to the partition.
   */
  void closeAll() {
    LOG.info("Stopping {} EventBridge partition(s)", rafts.size());

    for (final var component : components) {
      closeQuietly(component, "partition component");
    }
    components.clear();

    for (final var partition : rafts) {
      try {
        partition.raftPartition().close().get(CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      } catch (final Exception e) {
        LOG.warn("Error closing raft partition {}", partition.partitionId(), e);
      }
      try {
        // The FileBasedSnapshotStore is an actor.
        if (partition.snapshotStore() instanceof final Actor actor) {
          actor.closeAsync();
        }
      } catch (final Exception e) {
        LOG.warn("Error closing snapshot store for partition {}", partition.partitionId(), e);
      }
    }
    rafts.clear();
    dataReplicas.clear();
  }

  private static void closeQuietly(final Actor actor, final String description) {
    try {
      actor.closeAsync();
    } catch (final Exception e) {
      LOG.warn("Error closing {}", description, e);
    }
  }
}
