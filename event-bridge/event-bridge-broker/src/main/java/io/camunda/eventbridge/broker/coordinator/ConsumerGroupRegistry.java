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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * In-memory consumer group registry. Tracks live consumers, their assigned partitions, heartbeat
 * timestamps, committed offsets, and the current rebalance epoch.
 *
 * <p>This class is <em>not</em> thread-safe; it is intended to be accessed only from within the
 * coordinator broker's actor thread.
 */
public final class ConsumerGroupRegistry {

  private static final Logger LOG = LoggerFactory.getLogger(ConsumerGroupRegistry.class);

  /**
   * Maximum total in-flight revocations across all consumers in a group. Additional revocations are
   * deferred until the inflight count drops below this threshold.
   */
  private final int maxInflightRevocations;

  /**
   * Maximum time in milliseconds a consumer has to ACK a revocation or assignment before it is
   * evicted from the group. {@code 0} disables ACK-timeout eviction (used in tests that do not wire
   * ACK timeouts).
   */
  private final long ackTimeoutMs;

  /** Registered groups, keyed by group ID. */
  private final Map<String, ConsumerGroup> groups = new HashMap<>();

  public ConsumerGroupRegistry() {
    this(Integer.MAX_VALUE, 0L);
  }

  public ConsumerGroupRegistry(final int maxInflightRevocations) {
    this(maxInflightRevocations, 0L);
  }

  public ConsumerGroupRegistry(final int maxInflightRevocations, final long ackTimeoutMs) {
    this.maxInflightRevocations = maxInflightRevocations;
    this.ackTimeoutMs = ackTimeoutMs;
  }

  // -------------------------------------------------------------------------
  // Public result types

  /**
   * Delta returned by {@link #heartbeat}: the epoch and the partition lists the consumer must
   * revoke, accept, or reconcile from.
   */
  public record HeartbeatDelta(
      long epoch, List<Integer> revoke, List<Integer> assign, List<Integer> fullAssignment) {}

  /** Status returned by {@link #ack}. */
  public enum AckStatus {
    /** ACK accepted and state updated. */
    OK,
    /**
     * The ACK epoch does not match the coordinator epoch; the consumer will self-correct on the
     * next heartbeat via an epoch-advance full reconcile.
     */
    EPOCH_MISMATCH,
    /** Consumer ID is not known in this group; consumer should re-register via heartbeat. */
    CONSUMER_NOT_FOUND
  }

  // -------------------------------------------------------------------------
  // Registry operations

  /**
   * Upserts the consumer (auto-registering on first contact), refreshes its heartbeat timestamp and
   * owned-partition set, and computes the delta or full-assignment to return.
   *
   * <ul>
   *   <li>If {@code clientEpoch < group.epoch}: returns {@code fullAssignment} with the consumer's
   *       current target partitions so it can reconcile.
   *   <li>If {@code clientEpoch == group.epoch}: returns {@code revoke} and {@code assign} deltas.
   *   <li>If {@code clientEpoch > group.epoch}: unexpected; treated as equal (logs a warning).
   * </ul>
   *
   * <p>If this is the consumer's first heartbeat, it is auto-registered and a rebalance is
   * scheduled for the next coordinator loop cycle. The response for a brand-new consumer has empty
   * {@code revoke}/{@code assign}/{@code fullAssignment} lists because no assignment exists yet.
   *
   * @param groupId consumer group identifier
   * @param consumerId consumer identifier
   * @param clientEpoch epoch last seen by the consumer ({@code 0} for a new consumer)
   * @param ownedPartitions partition IDs the consumer currently holds
   * @param totalPartitions configured partition count for this group (used at group-creation time)
   * @return heartbeat delta
   */
  public HeartbeatDelta heartbeat(
      final String groupId,
      final String consumerId,
      final long clientEpoch,
      final Set<Integer> ownedPartitions,
      final int totalPartitions) {

    Objects.requireNonNull(groupId, "groupId must not be null");
    Objects.requireNonNull(consumerId, "consumerId must not be null");
    Objects.requireNonNull(ownedPartitions, "ownedPartitions must not be null");

    final ConsumerGroup group =
        groups.computeIfAbsent(groupId, id -> new ConsumerGroup(id, totalPartitions));

    final boolean isNew = !group.isActive(consumerId);
    group.upsert(consumerId, ownedPartitions);
    if (isNew) {
      group.consumersChanged = true;
      LOG.debug("Auto-registered consumer {}/{}", groupId, consumerId);
    }

    final long epoch = group.getEpoch();

    if (clientEpoch > epoch) {
      LOG.warn(
          "clientEpoch {} > coordinatorEpoch {} for {}/{} — treating as equal",
          clientEpoch,
          epoch,
          groupId,
          consumerId);
      // fall through to delta path
    } else if (clientEpoch < epoch) {
      // Consumer is behind — return full current assignment for reconciliation.
      final List<Integer> fullAssignment = group.getAssignedPartitions(consumerId);
      return new HeartbeatDelta(epoch, List.of(), List.of(), fullAssignment);
    }

    // Delta path (clientEpoch == epoch, or treated as equal)
    return group.computeDelta(consumerId, ownedPartitions, maxInflightRevocations, ackTimeoutMs);
  }

  /**
   * Processes an acknowledgement from a consumer confirming which partitions it has revoked and
   * which it has accepted.
   *
   * <p>If the ACK epoch does not match the coordinator epoch the ACK is silently discarded; the
   * consumer will self-correct on the next heartbeat. No error is surfaced to the caller.
   *
   * @param groupId consumer group identifier
   * @param consumerId consumer identifier
   * @param epoch the coordinator epoch the consumer observed when sending the ACK
   * @param revoked partition IDs the consumer confirms it has stopped processing
   * @param assigned partition IDs the consumer confirms it has started processing
   * @return {@link AckStatus}
   */
  public AckStatus ack(
      final String groupId,
      final String consumerId,
      final long epoch,
      final List<Integer> revoked,
      final List<Integer> assigned) {

    Objects.requireNonNull(groupId, "groupId must not be null");
    Objects.requireNonNull(consumerId, "consumerId must not be null");

    final ConsumerGroup group = groups.get(groupId);
    if (group == null || !group.isActive(consumerId)) {
      return AckStatus.CONSUMER_NOT_FOUND;
    }

    if (epoch != group.getEpoch()) {
      LOG.warn(
          "Stale ACK epoch {} != group epoch {} for {}/{} — discarding",
          epoch,
          group.getEpoch(),
          groupId,
          consumerId);
      return AckStatus.EPOCH_MISMATCH;
    }

    group.applyAck(consumerId, revoked, assigned);
    return AckStatus.OK;
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
   * Evicts all consumers whose last heartbeat is older than {@code deadline}, checks for partition
   * count changes, and triggers a BALANCED_STICKY rebalance for any group with pending membership
   * changes (new consumers, evictions, or partition count mismatches).
   *
   * @param deadline consumers with {@code lastHeartbeat < deadline} are considered dead
   * @param fallbackTotalPartitions partition count to use for groups that do not have a stored
   *     {@code configuredPartitionCount} when the group was created
   */
  public void evictDeadConsumers(final Instant deadline, final int fallbackTotalPartitions) {
    for (final ConsumerGroup group : groups.values()) {
      final boolean evicted = group.evictDead(deadline);
      if (evicted) {
        group.consumersChanged = true;
      }

      final int partitionCount =
          group.configuredPartitionCount > 0
              ? group.configuredPartitionCount
              : fallbackTotalPartitions;

      // Detect partition count change: after the first rebalance, the tracked partition set must
      // match configuredPartitionCount. A mismatch means the count was updated (e.g., via admin
      // API) and a reconciliation rebalance is needed.
      if (!group.activeConsumers.isEmpty()
          && group.getEpoch() > 0
          && group.getPartitionAssignment().size() != partitionCount) {
        LOG.info(
            "Partition count change detected for group {}: tracked={}, configured={}; "
                + "triggering rebalance",
            group.getGroupId(),
            group.getPartitionAssignment().size(),
            partitionCount);
        group.consumersChanged = true;
      }

      if (group.consumersChanged) {
        group.rebalance(partitionCount);
        group.consumersChanged = false;
      }
    }
  }

  /**
   * Evicts consumers whose {@code ackDeadline} has expired (i.e., they failed to ACK a revocation
   * or assignment within {@code ackTimeoutMs}). Evicted consumers are removed from their group's
   * active registry, and their groups are marked as changed so {@link #evictDeadConsumers(Instant,
   * int)} triggers a rebalance on the same coordinator loop cycle.
   *
   * <p>This is the spec-mandated behavior: an ACK timeout is treated the same as a session timeout
   * — the consumer is fully evicted and its partitions are redistributed.
   *
   * @param now current instant used to evaluate expired deadlines
   */
  public void expireAckTimeouts(final Instant now) {
    for (final ConsumerGroup group : groups.values()) {
      final boolean evicted = group.evictExpiredAckDeadlines(now);
      if (evicted) {
        group.consumersChanged = true;
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

    /**
     * Partition count configured at group-creation time. Used for rebalance and partition-count
     * change detection. {@code 0} is a legacy sentinel for groups created before this field existed
     * (the {@code evictDeadConsumers} fallback handles those cases).
     *
     * <p>Non-final to support future dynamic partition count changes via an admin endpoint ({@code
     * PUT /v1/groups/{groupId}/config}). The coordinator loop detects a mismatch between this value
     * and the tracked partition set and triggers a reconciliation rebalance.
     */
    int configuredPartitionCount;

    /** Active (alive) consumers, keyed by consumer ID. */
    private final Map<String, ConsumerEntry> activeConsumers = new HashMap<>();

    /** Partition assignment: {@code partitionId → consumerId}. Updated on every rebalance. */
    private final Map<Integer, String> partitionAssignment = new HashMap<>();

    /**
     * Rebalance epoch. Starts at {@code 0} (no rebalance yet) and increments to {@code 1} on the
     * first rebalance, then monotonically from there. New consumers send {@code clientEpoch = 0}
     * and will receive a full-assignment response once the coordinator loop has run.
     */
    private long epoch = 0L;

    /**
     * Set to {@code true} when membership changes (new consumer or eviction) require a rebalance.
     * Cleared to {@code false} after {@link #rebalance(int)} completes.
     */
    boolean consumersChanged = false;

    ConsumerGroup(final String groupId, final int configuredPartitionCount) {
      this.groupId = groupId;
      this.configuredPartitionCount = configuredPartitionCount;
    }

    public String getGroupId() {
      return groupId;
    }

    /** Returns the current rebalance epoch. */
    public long getEpoch() {
      return epoch;
    }

    /** Returns an unmodifiable view of the current partition assignment. */
    public Map<Integer, String> getPartitionAssignment() {
      return Collections.unmodifiableMap(partitionAssignment);
    }

    /** Returns the partitions currently assigned to {@code consumerId}, sorted ascending. */
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
     * Returns the consumer IDs of alive consumers assigned to {@code partitionId} in this group, or
     * an empty set if no alive consumer is currently assigned.
     */
    public Set<String> getAliveAssignedConsumersForPartition(final int partitionId) {
      final String assignedConsumer = partitionAssignment.get(partitionId);
      if (assignedConsumer != null && activeConsumers.containsKey(assignedConsumer)) {
        return Set.of(assignedConsumer);
      }
      return Set.of();
    }

    // -------------------------------------------------------------------------
    // Package-private mutation helpers

    /**
     * Upserts the consumer: creates a new entry if the consumer is unknown, or refreshes its
     * heartbeat timestamp and owned-partition set if it already exists.
     */
    void upsert(final String consumerId, final Set<Integer> ownedPartitions) {
      activeConsumers.compute(
          consumerId,
          (id, existing) -> {
            if (existing == null) {
              final var entry = new ConsumerEntry(id, Instant.now());
              entry.ownedPartitions.addAll(ownedPartitions);
              return entry;
            }
            existing.lastHeartbeat = Instant.now();
            existing.ownedPartitions.clear();
            existing.ownedPartitions.addAll(ownedPartitions);
            return existing;
          });
    }

    /**
     * Adds a new consumer or refreshes the heartbeat timestamp of an already-active one. Does not
     * update {@code ownedPartitions}.
     *
     * @return {@code true} if the consumer was newly added (membership changed)
     * @deprecated Use {@link #upsert(String, Set)} from the {@link #heartbeat} path.
     */
    @Deprecated
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
     * BALANCED_STICKY rebalance: existing assignments to still-alive consumers are preserved;
     * orphaned partitions are redistributed to the least-loaded active consumers (tie-broken
     * lexicographically). Increments the epoch.
     *
     * <p>Assignments for partition IDs {@code >= totalPartitions} are removed so that a restart
     * with a smaller partition count never leaves stale entries.
     */
    void rebalance(final int totalPartitions) {
      epoch++;

      if (activeConsumers.isEmpty()) {
        partitionAssignment.clear();
        return;
      }

      final List<String> sorted = new ArrayList<>(activeConsumers.keySet());
      sorted.sort(Comparator.naturalOrder());

      partitionAssignment.values().retainAll(activeConsumers.keySet());
      partitionAssignment.keySet().removeIf(p -> p >= totalPartitions);

      final List<Integer> orphaned = new ArrayList<>();
      for (int p = 0; p < totalPartitions; p++) {
        if (!partitionAssignment.containsKey(p)) {
          orphaned.add(p);
        }
      }

      final Map<String, Integer> load = new HashMap<>();
      for (final String c : sorted) {
        load.put(c, 0);
      }
      for (final String assignee : partitionAssignment.values()) {
        load.merge(assignee, 1, Integer::sum);
      }

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

    /**
     * Computes the assignment delta for {@code consumerId}: partitions to revoke (owned but not in
     * target) and partitions to assign (in target but not yet owned). Excludes any partitions
     * already pending revocation or assignment from a previous heartbeat cycle.
     *
     * <p>New revocations are capped so that the total in-flight revocations across all consumers in
     * this group never exceeds {@code maxInflightRevocations}. Revocations beyond the cap are
     * deferred to the next heartbeat cycle.
     *
     * <p>When a non-empty delta is returned, {@link ConsumerEntry#ackDeadline} is set to {@code now
     * + ackTimeoutMs} so the coordinator loop can evict the consumer if it fails to ACK within the
     * timeout window.
     */
    HeartbeatDelta computeDelta(
        final String consumerId,
        final Set<Integer> ownedPartitions,
        final int maxInflightRevocations,
        final long ackTimeoutMs) {
      final ConsumerEntry entry = activeConsumers.get(consumerId);
      if (entry == null) {
        return new HeartbeatDelta(epoch, List.of(), List.of(), List.of());
      }

      final Set<Integer> target = new HashSet<>(getAssignedPartitions(consumerId));

      final int currentInflight =
          activeConsumers.values().stream().mapToInt(e -> e.pendingRevoke.size()).sum();
      final int availableCapacity = Math.max(0, maxInflightRevocations - currentInflight);

      final List<Integer> revoke =
          ownedPartitions.stream()
              .filter(p -> !target.contains(p))
              .filter(p -> !entry.pendingRevoke.contains(p))
              .sorted()
              .limit(availableCapacity)
              .collect(Collectors.toList());

      final List<Integer> assign =
          target.stream()
              .filter(p -> !ownedPartitions.contains(p))
              .filter(p -> !entry.pendingAssign.contains(p))
              .sorted()
              .collect(Collectors.toList());

      entry.pendingRevoke.addAll(revoke);
      entry.pendingAssign.addAll(assign);

      if ((!revoke.isEmpty() || !assign.isEmpty()) && ackTimeoutMs > 0) {
        // Always extend the deadline when new work is added so the consumer gets
        // the full ackTimeoutMs window for each batch, even when a prior batch is
        // still pending (e.g. revocations deferred by the maxInflightRevocations cap).
        entry.ackDeadline = Instant.now().plusMillis(ackTimeoutMs);
      }

      return new HeartbeatDelta(epoch, revoke, assign, List.of());
    }

    /**
     * Applies an ACK: clears pending-revoke entries for the confirmed {@code revoked} partitions
     * and pending-assign entries for the confirmed {@code assigned} partitions. Updates the
     * consumer's owned-partition set accordingly.
     *
     * <p>When no pending items remain after this ACK, {@link ConsumerEntry#ackDeadline} is cleared
     * so the consumer is not incorrectly evicted on the next coordinator loop cycle.
     */
    void applyAck(
        final String consumerId, final List<Integer> revoked, final List<Integer> assigned) {
      final ConsumerEntry entry = activeConsumers.get(consumerId);
      if (entry == null) {
        return;
      }
      entry.pendingRevoke.removeAll(revoked);
      entry.ownedPartitions.removeAll(revoked);
      entry.pendingAssign.removeAll(assigned);
      entry.ownedPartitions.addAll(assigned);

      // Clear the ACK deadline once there are no more pending items; the next heartbeat that
      // produces a non-empty delta will re-arm the deadline.
      if (entry.pendingRevoke.isEmpty() && entry.pendingAssign.isEmpty()) {
        entry.ackDeadline = null;
      }
    }

    /**
     * Evicts consumers whose {@link ConsumerEntry#ackDeadline} has passed (they failed to ACK a
     * revocation or assignment within the configured timeout). Evicted consumers are removed from
     * the active set; their partition assignments are cleaned up by the subsequent {@link
     * #rebalance(int)} call in the coordinator loop.
     *
     * @param now current instant used to evaluate expired deadlines
     * @return {@code true} if at least one consumer was evicted
     */
    boolean evictExpiredAckDeadlines(final Instant now) {
      final List<String> toEvict = new ArrayList<>();
      for (final ConsumerEntry entry : activeConsumers.values()) {
        if (entry.ackDeadline != null && entry.ackDeadline.isBefore(now)) {
          toEvict.add(entry.consumerId);
        }
      }
      for (final String consumerId : toEvict) {
        activeConsumers.remove(consumerId);
        LOG.warn("ACK timeout expired for consumer {} in group {}; evicting", consumerId, groupId);
      }
      return !toEvict.isEmpty();
    }

    // -------------------------------------------------------------------------

    /** Mutable per-consumer entry. */
    static final class ConsumerEntry {
      final String consumerId;
      Instant lastHeartbeat;

      /** Partition IDs the consumer most recently reported as owned. */
      final Set<Integer> ownedPartitions = new HashSet<>();

      /** Partitions sent for revocation in a heartbeat response, awaiting ACK. */
      final Set<Integer> pendingRevoke = new HashSet<>();

      /** Partitions sent for assignment in a heartbeat response, awaiting ACK. */
      final Set<Integer> pendingAssign = new HashSet<>();

      /**
       * Absolute deadline by which this consumer must ACK its outstanding revocations/assignments.
       * Set to {@code now + ackTimeoutMs} whenever a heartbeat response includes a non-empty {@code
       * revoke} or {@code assign} list. Cleared (set to {@code null}) once both {@link
       * #pendingRevoke} and {@link #pendingAssign} are empty. When this deadline is exceeded, the
       * coordinator loop evicts the consumer (same treatment as a session timeout).
       */
      Instant ackDeadline;

      ConsumerEntry(final String consumerId, final Instant lastHeartbeat) {
        this.consumerId = consumerId;
        this.lastHeartbeat = lastHeartbeat;
      }
    }
  }
}
