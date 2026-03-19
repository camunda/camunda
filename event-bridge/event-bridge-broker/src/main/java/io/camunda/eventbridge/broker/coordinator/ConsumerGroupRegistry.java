/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.coordinator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * In-memory consumer group registry. Tracks live consumers, their assigned partitions, heartbeat
 * timestamps, committed offsets, and the current rebalance generation.
 *
 * <p>This class is <em>not</em> thread-safe; it is intended to be accessed only from within the
 * coordinator broker's actor thread.
 */
public final class ConsumerGroupRegistry {

  /** Registered groups, keyed by group ID. */
  private final Map<String, ConsumerGroup> groups = new HashMap<>();

  /**
   * Registers a consumer in its group (or re-registers an existing one after a crash), immediately
   * triggers a rebalance if the membership actually changed. A simple heartbeat renewal (already
   * active consumer calling subscribe again without having died) does not increment the generation.
   *
   * @param groupId the consumer group identifier
   * @param consumerId the individual consumer identifier within the group
   * @param totalPartitions total number of partitions managed by this broker
   * @return the updated {@link ConsumerGroup} after rebalance
   */
  public ConsumerGroup subscribe(
      final String groupId, final String consumerId, final int totalPartitions) {
    Objects.requireNonNull(groupId, "groupId must not be null");
    Objects.requireNonNull(consumerId, "consumerId must not be null");
    final ConsumerGroup group = groups.computeIfAbsent(groupId, ConsumerGroup::new);
    final boolean membershipChanged = group.addOrRefresh(consumerId);
    if (membershipChanged) {
      group.rebalance(totalPartitions);
    }
    return group;
  }

  /**
   * Records a heartbeat for the given consumer.
   *
   * @return {@code true} if the consumer is registered and alive; {@code false} if the consumer is
   *     unknown or has been marked dead
   */
  public boolean heartbeat(final String groupId, final String consumerId) {
    Objects.requireNonNull(groupId, "groupId must not be null");
    Objects.requireNonNull(consumerId, "consumerId must not be null");
    final ConsumerGroup group = groups.get(groupId);
    if (group == null) {
      return false;
    }
    return group.heartbeat(consumerId);
  }

  /**
   * Returns {@code true} if the consumer is currently active (registered and not evicted) in the
   * given group.
   */
  public boolean isConsumerActive(final String groupId, final String consumerId) {
    final ConsumerGroup group = groups.get(groupId);
    if (group == null) {
      return false;
    }
    return group.isActive(consumerId);
  }

  /**
   * Evicts all consumers whose last heartbeat is older than the given deadline and rebalances
   * affected groups.
   *
   * @param deadline consumers with {@code lastHeartbeat < deadline} are considered dead
   * @param totalPartitions total number of partitions
   */
  public void evictDeadConsumers(final Instant deadline, final int totalPartitions) {
    for (final ConsumerGroup group : groups.values()) {
      final boolean changed = group.evictDead(deadline);
      if (changed) {
        group.rebalance(totalPartitions);
      }
    }
  }

  /** Returns the group for the given ID, or {@code null} if it does not exist. */
  public ConsumerGroup getGroup(final String groupId) {
    return groups.get(groupId);
  }

  /** Returns all groups (unmodifiable view). */
  public Map<String, ConsumerGroup> getAllGroups() {
    return Collections.unmodifiableMap(groups);
  }

  // -------------------------------------------------------------------------

  /** State for a single consumer group. */
  public static final class ConsumerGroup {

    private final String groupId;

    /** Active (alive) consumers, keyed by consumer ID. */
    private final Map<String, ConsumerEntry> activeConsumers = new HashMap<>();

    /** Partition assignment: {@code partitionId → consumerId}. Updated on every rebalance. */
    private final Map<Integer, String> partitionAssignment = new HashMap<>();

    /** Monotonically incrementing generation counter; starts at 0 and incremented on subscribe. */
    private long generation = 0L;

    ConsumerGroup(final String groupId) {
      this.groupId = groupId;
    }

    public String getGroupId() {
      return groupId;
    }

    public long getGeneration() {
      return generation;
    }

    /** Returns an unmodifiable view of the current partition assignment. */
    public Map<Integer, String> getPartitionAssignment() {
      return Collections.unmodifiableMap(partitionAssignment);
    }

    /** Returns the partitions currently assigned to {@code consumerId}. */
    public List<Integer> getAssignedPartitions(final String consumerId) {
      final List<Integer> result = new ArrayList<>();
      for (final var entry : partitionAssignment.entrySet()) {
        if (consumerId.equals(entry.getValue())) {
          result.add(entry.getKey());
        }
      }
      Collections.sort(result);
      return result;
    }

    /**
     * Returns {@code true} if {@code consumerId} is currently in the active (alive) consumer set.
     */
    public boolean isActive(final String consumerId) {
      return activeConsumers.containsKey(consumerId);
    }

    /**
     * Returns the consumer ID of the alive consumer assigned to {@code partitionId} within this
     * group, or an empty set if no alive consumer is currently assigned to that partition.
     * Returning only consumer IDs keeps this class decoupled from the {@code offset} package.
     */
    public Set<String> getAliveAssignedConsumersForPartition(final int partitionId) {
      final String assignedConsumer = partitionAssignment.get(partitionId);
      if (assignedConsumer != null && activeConsumers.containsKey(assignedConsumer)) {
        return Set.of(assignedConsumer);
      }
      return Set.of();
    }

    /**
     * Adds a new consumer or refreshes the heartbeat of an already-active one.
     *
     * @return {@code true} if the consumer was newly added (membership changed); {@code false} if
     *     the consumer was already active (heartbeat refreshed only, no rebalance needed)
     */
    boolean addOrRefresh(final String consumerId) {
      final boolean[] isNew = {false};
      activeConsumers.compute(
          consumerId,
          (id, existing) -> {
            if (existing == null) {
              isNew[0] = true;
              return new ConsumerEntry(id, Instant.now());
            }
            existing.lastHeartbeat = Instant.now();
            return existing;
          });
      return isNew[0];
    }

    boolean heartbeat(final String consumerId) {
      final ConsumerEntry entry = activeConsumers.get(consumerId);
      if (entry == null) {
        return false;
      }
      entry.lastHeartbeat = Instant.now();
      return true;
    }

    /**
     * Evicts consumers whose last heartbeat precedes {@code deadline}.
     *
     * @return {@code true} if at least one consumer was evicted
     */
    boolean evictDead(final Instant deadline) {
      final int before = activeConsumers.size();
      activeConsumers.values().removeIf(e -> e.lastHeartbeat.isBefore(deadline));
      return activeConsumers.size() < before;
    }

    /**
     * Performs a stable round-robin rebalance: existing assignments to still-alive consumers are
     * preserved; orphaned partitions are redistributed to the least-loaded active consumers.
     * Consumers are sorted lexicographically by ID.
     *
     * <p>Assignments for partition IDs {@code >= totalPartitions} are also removed, so that a
     * restart with a smaller partition count never leaves stale entries in the map.
     */
    void rebalance(final int totalPartitions) {
      generation++;

      if (activeConsumers.isEmpty()) {
        partitionAssignment.clear();
        return;
      }

      // Sort consumers deterministically (lexicographic Unicode code-point order)
      final List<String> sorted = new ArrayList<>(activeConsumers.keySet());
      sorted.sort(Comparator.naturalOrder());

      // Remove assignments for dead consumers
      partitionAssignment.values().retainAll(activeConsumers.keySet());

      // Remove assignments for partitions outside the current partition range
      partitionAssignment.keySet().removeIf(p -> p >= totalPartitions);

      // Collect orphaned partitions (those not assigned to an active consumer)
      final List<Integer> orphaned = new ArrayList<>();
      for (int p = 0; p < totalPartitions; p++) {
        if (!partitionAssignment.containsKey(p)) {
          orphaned.add(p);
        }
      }

      // Count current load per active consumer
      final Map<String, Integer> load = new HashMap<>();
      for (final String c : sorted) {
        load.put(c, 0);
      }
      for (final String assignee : partitionAssignment.values()) {
        load.merge(assignee, 1, Integer::sum);
      }

      // Assign orphaned partitions to the currently least-loaded consumer (tie-broken by sort)
      for (final int p : orphaned) {
        final String target =
            sorted.stream()
                .min(
                    Comparator.comparingInt((String c) -> load.getOrDefault(c, 0))
                        .thenComparing(Comparator.naturalOrder()))
                .orElseThrow();
        partitionAssignment.put(p, target);
        load.merge(target, 1, Integer::sum);
      }
    }

    /** Mutable per-consumer entry. */
    static final class ConsumerEntry {
      final String consumerId;
      Instant lastHeartbeat;

      ConsumerEntry(final String consumerId, final Instant lastHeartbeat) {
        this.consumerId = consumerId;
        this.lastHeartbeat = lastHeartbeat;
      }
    }
  }
}
