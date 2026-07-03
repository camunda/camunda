/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.client.internal.consumer;

import io.camunda.eventbridge.client.OffsetResetPolicy;
import io.camunda.eventbridge.client.TopicPartition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holds a consumer's subscription state: the (topic, partition)s it currently owns, the
 * per-partition fetch position ({@code nextPosition}), caller-requested seek positions, and the
 * resolution of a newly assigned partition's start position via the configured {@link
 * OffsetResetPolicy}.
 *
 * <p>Positions are stored in concurrent maps and read from both caller threads and background fetch
 * threads. Composite updates that must be atomic with a buffer mutation (seek, reassignment) are
 * run by the consumer under the {@link PrefetchBuffer} lock.
 */
public final class SubscriptionState {

  private static final Logger LOG = LoggerFactory.getLogger(SubscriptionState.class);

  /** Sentinel for a newly assigned partition whose start offset has not been resolved yet. */
  public static final long UNSET_POSITION = Long.MIN_VALUE;

  private final Fetcher fetcher;
  private final OffsetResetPolicy offsetResetPolicy;

  /**
   * The (topic, partition)s currently owned by this consumer (sorted). Replaced wholesale on every
   * heartbeat that returns a delta or full-reconciliation signal.
   */
  private volatile List<TopicPartition> ownedPartitions = List.of();

  /**
   * Per-partition fetch position. Initialized to {@code UNSET_POSITION} for newly assigned
   * partitions; updated after each successful fetch.
   */
  private final ConcurrentHashMap<TopicPartition, Long> nextPositions = new ConcurrentHashMap<>();

  /**
   * Caller-requested start positions (see the consumer's {@code seek}). Consulted when a partition
   * is assigned, so the consumer resumes from a position the caller has durably checkpointed rather
   * than from the coordinator's committed offset or the reset policy.
   */
  private final ConcurrentHashMap<TopicPartition, Long> startPositions = new ConcurrentHashMap<>();

  public SubscriptionState(final Fetcher fetcher, final OffsetResetPolicy offsetResetPolicy) {
    this.fetcher = fetcher;
    this.offsetResetPolicy = offsetResetPolicy;
  }

  /** Returns an unmodifiable snapshot of the currently owned (topic, partition)s. */
  public List<TopicPartition> ownedPartitions() {
    return ownedPartitions;
  }

  /** True if {@code tp} is currently owned. */
  public boolean owns(final TopicPartition tp) {
    return ownedPartitions.contains(tp);
  }

  /**
   * Clears the owned-partition list without touching fetch positions or buffers (used on leave).
   */
  public void clearOwnedPartitions() {
    ownedPartitions = Collections.emptyList();
  }

  /** Records a caller-requested seek position for {@code tp}. */
  public void recordSeek(final TopicPartition tp, final long position) {
    startPositions.put(tp, position);
    nextPositions.merge(tp, position, Math::max);
  }

  /**
   * Forces {@code tp} back to the start of the log (offset {@code 0}), overriding any advanced
   * cursor — unlike {@link #recordSeek}, which only advances. Used to rebuild per-partition state
   * by replaying from the beginning; the broker's reset policy clamps to the oldest retained record
   * if offset {@code 0} has been compacted/aged out.
   */
  public void rewindToBeginning(final TopicPartition tp) {
    startPositions.put(tp, 0L);
    nextPositions.put(tp, 0L);
  }

  /** Returns the next fetch position for {@code tp}, defaulting to oldest-retained ({@code -1}). */
  public long nextPosition(final TopicPartition tp) {
    return nextPositions.getOrDefault(tp, -1L);
  }

  /** Sets the next fetch position for {@code tp}. */
  public void setNextPosition(final TopicPartition tp, final long position) {
    nextPositions.put(tp, position);
  }

  /**
   * Replaces {@code ownedPartitions} with a sorted copy of {@code partitions}, dropping revoked
   * partitions from {@code nextPositions} and initialising newly assigned ones to a caller seek (if
   * any) or {@link #UNSET_POSITION}. Caller runs this under the buffer lock together with the
   * buffer retain/generation bump.
   */
  public void applyOwnedPartitions(final List<TopicPartition> partitions) {
    final var sorted = new ArrayList<>(partitions);
    Collections.sort(sorted);
    nextPositions.keySet().retainAll(sorted);
    for (final var tp : sorted) {
      // Newly assigned: prefer a caller-requested start (seek), else start unresolved. A committed
      // offset (seedCommittedOffsets) or the reset policy (resolved on first fetch) then determines
      // where an unresolved partition actually starts.
      nextPositions.putIfAbsent(tp, startPositions.getOrDefault(tp, UNSET_POSITION));
    }
    ownedPartitions = Collections.unmodifiableList(sorted);
  }

  /** Returns the sorted list this state would own for {@code partitions} (without applying it). */
  public List<TopicPartition> sorted(final List<TopicPartition> partitions) {
    final var sorted = new ArrayList<>(partitions);
    Collections.sort(sorted);
    return sorted;
  }

  /**
   * Seeds {@code nextPositions} from the coordinator's committed offsets carried in a heartbeat
   * response, so a (re)assigned consumer resumes from the committed position. Uses {@code max} so
   * an in-flight local position is never rewound to an older committed one.
   */
  public void seedCommittedOffsets(final Map<String, Map<Integer, Long>> committed) {
    // committedOffsets is grouped {topic -> {partition -> offset}}.
    if (committed == null) {
      return;
    }
    committed.forEach(
        (topic, offsets) -> {
          if (offsets == null) {
            return;
          }
          offsets.forEach(
              (partition, position) -> {
                if (position == null) {
                  return;
                }
                final var tp = new TopicPartition(topic, partition);
                if (ownedPartitions.contains(tp)) {
                  nextPositions.merge(tp, position, Math::max);
                }
              });
        });
  }

  /**
   * Resolves the start position for a newly assigned partition that has no committed offset, per
   * the configured {@link OffsetResetPolicy}: {@code EARLIEST} → oldest retained ({@code -1});
   * {@code LATEST} → just past the current end of the log (skip existing records). Falls back to
   * earliest on error.
   */
  public long resolveStartPosition(final TopicPartition tp) {
    if (offsetResetPolicy == OffsetResetPolicy.LATEST) {
      try {
        final long highWatermark =
            fetcher.fetchFromTopic(tp.topic(), tp.partition(), 0, 4096).join().highWatermark();
        return highWatermark < 0 ? 0L : highWatermark + 1;
      } catch (final RuntimeException e) {
        LOG.warn("Failed to resolve LATEST start for {}; falling back to earliest", tp, e);
        return -1L;
      }
    }
    return -1L; // EARLIEST
  }
}
