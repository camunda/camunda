/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.offset;

import io.camunda.eventbridge.core.protocol.MessageHeaderDecoder;
import io.camunda.eventbridge.core.protocol.MessageHeaderEncoder;
import io.camunda.eventbridge.core.protocol.OffsetSnapshotPayloadDecoder;
import io.camunda.eventbridge.core.protocol.OffsetSnapshotPayloadEncoder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * In-memory store for committed consumer offsets, with snapshot serialization support.
 *
 * <p>Tracks the last committed log position for each {@code (groupId, consumerId, partitionId)}
 * triple. Offsets are retained even after a consumer is evicted from the active set so that
 * re-subscribing consumers can resume from their last committed position and immediately re-enter
 * the truncation boundary calculation.
 *
 * <p>Snapshot serialization uses the SBE-encoded {@link
 * io.camunda.eventbridge.core.protocol.OffsetSnapshotPayloadEncoder OffsetSnapshotPayload} message.
 * Each RAFT partition has its own on-disk snapshot file ({@value #SNAPSHOT_FILE_NAME}); a single
 * {@code OffsetStore} instance covers all partitions and produces/consumes per-partition blobs.
 *
 * <p>Not thread-safe; intended to be accessed exclusively from the coordinator actor thread.
 */
public final class OffsetStore {

  /** File name written inside the RAFT snapshot directory for each partition. */
  public static final String SNAPSHOT_FILE_NAME = "offsets.sbe";

  // groupId -> consumerId -> partitionId -> committedPosition
  // TODO: add a purge(groupId, consumerId) hook for long-running deployments with high consumer
  //       churn; without it the map grows without bound for every consumer that has ever committed.
  private final Map<String, Map<String, Map<Integer, Long>>> offsets = new HashMap<>();

  // -------------------------------------------------------------------------
  // Core offset operations

  /**
   * Records a committed position for {@code (groupId, consumerId, partitionId)}. Idempotent:
   * silently accepted without updating the stored offset when {@code position} is less than or
   * equal to the currently stored value.
   *
   * @throws NullPointerException if {@code groupId} or {@code consumerId} is {@code null}
   */
  public void commit(
      final String groupId, final String consumerId, final int partitionId, final long position) {
    Objects.requireNonNull(groupId, "groupId must not be null");
    Objects.requireNonNull(consumerId, "consumerId must not be null");
    final long current =
        offsets
            .computeIfAbsent(groupId, g -> new HashMap<>())
            .computeIfAbsent(consumerId, c -> new HashMap<>())
            .getOrDefault(partitionId, Long.MIN_VALUE);
    if (position > current) {
      offsets.get(groupId).get(consumerId).put(partitionId, position);
    }
  }

  /**
   * Returns the committed offset for {@code (groupId, consumerId, partitionId)}, or {@code -1} if
   * no commit has been recorded.
   *
   * @throws NullPointerException if {@code groupId} or {@code consumerId} is {@code null}
   */
  public long getCommittedOffset(
      final String groupId, final String consumerId, final int partitionId) {
    Objects.requireNonNull(groupId, "groupId must not be null");
    Objects.requireNonNull(consumerId, "consumerId must not be null");
    final var byConsumer = offsets.get(groupId);
    if (byConsumer == null) {
      return -1L;
    }
    final var byPartition = byConsumer.get(consumerId);
    if (byPartition == null) {
      return -1L;
    }
    return byPartition.getOrDefault(partitionId, -1L);
  }

  // -------------------------------------------------------------------------
  // Truncation boundary

  /**
   * Computes the truncation boundary for {@code partitionId}: the minimum committed position across
   * all {@code aliveAssignedConsumers} that have committed at least once to this partition.
   *
   * <p>Returns {@link Long#MAX_VALUE} when the eligible set is empty or no consumer in the set has
   * committed to this partition — signalling that no truncation is safe.
   *
   * <p>Dead consumers (those not present in {@code aliveAssignedConsumers}) are excluded from the
   * calculation, preventing a stalled consumer from pinning the log indefinitely.
   */
  public long getTruncationBoundary(
      final int partitionId, final Set<ConsumerKey> aliveAssignedConsumers) {
    long min = Long.MAX_VALUE;
    boolean found = false;
    for (final ConsumerKey key : aliveAssignedConsumers) {
      final long pos = getCommittedOffset(key.groupId(), key.consumerId(), partitionId);
      if (pos >= 0) {
        min = Math.min(min, pos);
        found = true;
      }
    }
    return found ? min : Long.MAX_VALUE;
  }

  // -------------------------------------------------------------------------
  // Snapshot serialization — per-partition SBE encoding

  /**
   * Serializes all offset entries for {@code partitionId} to an SBE-encoded byte array (prefixed
   * with a {@link MessageHeaderEncoder}). The resulting bytes can be written to the RAFT snapshot
   * directory via {@link #saveToDirectory(int, Path)}.
   *
   * <p>The format is an {@code OffsetSnapshotPayload} message containing one {@code offsets} group
   * element per {@code (groupId, consumerId)} pair that has a committed position on this partition.
   */
  public byte[] serializeForPartition(final int partitionId) {
    final List<OffsetEntry> entries = getEntriesForPartition(partitionId);

    // Pre-size buffer: header + group header + N * (8 bytes fixed + ~32 bytes var data estimate)
    final int estimatedSize =
        MessageHeaderEncoder.ENCODED_LENGTH
            + OffsetSnapshotPayloadEncoder.OffsetsEncoder.HEADER_SIZE
            + entries.size() * 72;
    final var buf = new ExpandableArrayBuffer(Math.max(estimatedSize, 64));
    final var headerEncoder = new MessageHeaderEncoder();
    final var payloadEncoder = new OffsetSnapshotPayloadEncoder();
    payloadEncoder.wrapAndApplyHeader(buf, 0, headerEncoder);

    final var offsetsEncoder = payloadEncoder.offsetsCount(entries.size());
    for (final OffsetEntry e : entries) {
      offsetsEncoder
          .next()
          .committedPosition(e.committedPosition())
          .groupId(e.groupId())
          .consumerId(e.consumerId());
    }

    final int totalLen = MessageHeaderEncoder.ENCODED_LENGTH + payloadEncoder.encodedLength();
    final byte[] result = new byte[totalLen];
    buf.getBytes(0, result);
    return result;
  }

  /**
   * Replaces all stored entries for {@code partitionId} with those deserialized from the given
   * SBE-encoded byte array (as produced by {@link #serializeForPartition(int)}). Entries for other
   * partitions are not affected.
   *
   * <p>A {@code null} or empty {@code data} array is treated as an empty snapshot: no entries are
   * added, and any existing data for the partition is preserved (the store is not cleared). This
   * prevents accidental data loss when a snapshot file is missing or empty.
   */
  public void deserializeForPartition(final int partitionId, final byte[] data) {
    if (data == null || data.length == 0) {
      return;
    }

    // Remove existing entries for this partition only after confirming valid data is available
    offsets
        .values()
        .forEach(byConsumer -> byConsumer.values().forEach(byPart -> byPart.remove(partitionId)));

    final var buf = new UnsafeBuffer(data);
    final var headerDecoder = new MessageHeaderDecoder();
    final var payloadDecoder = new OffsetSnapshotPayloadDecoder();
    payloadDecoder.wrapAndApplyHeader(buf, 0, headerDecoder);

    for (final var item : payloadDecoder.offsets()) {
      final long pos = item.committedPosition();
      final String groupId = item.groupId();
      final String consumerId = item.consumerId();
      offsets
          .computeIfAbsent(groupId, g -> new HashMap<>())
          .computeIfAbsent(consumerId, c -> new HashMap<>())
          .put(partitionId, pos);
    }
  }

  // -------------------------------------------------------------------------
  // Snapshot file I/O

  /**
   * Writes the offset state for {@code partitionId} as a binary file ({@value #SNAPSHOT_FILE_NAME}
   * ) inside {@code snapshotDir}. Called during RAFT snapshot creation.
   *
   * @param partitionId the partition whose offsets to persist
   * @param snapshotDir the RAFT snapshot directory (must already exist)
   * @throws IOException on file-system errors
   */
  public void saveToDirectory(final int partitionId, final Path snapshotDir) throws IOException {
    Files.write(snapshotDir.resolve(SNAPSHOT_FILE_NAME), serializeForPartition(partitionId));
  }

  /**
   * Loads offset state for {@code partitionId} from a snapshot directory. The expected file is
   * {@code snapshotDir}/{@value #SNAPSHOT_FILE_NAME}. A missing file is treated as an empty store
   * (no-op). Called during broker startup or RAFT leader failover recovery.
   *
   * @param partitionId the partition to restore
   * @param snapshotDir the RAFT snapshot directory
   * @throws IOException on file-system errors
   */
  public void loadFromDirectory(final int partitionId, final Path snapshotDir) throws IOException {
    final Path file = snapshotDir.resolve(SNAPSHOT_FILE_NAME);
    if (!Files.exists(file)) {
      return;
    }
    deserializeForPartition(partitionId, Files.readAllBytes(file));
  }

  // -------------------------------------------------------------------------
  // Inspection helpers

  /**
   * Returns all offset entries for the given {@code partitionId} as an unmodifiable list. Useful
   * for snapshot creation and diagnostics.
   */
  public List<OffsetEntry> getEntriesForPartition(final int partitionId) {
    final var result = new ArrayList<OffsetEntry>();
    offsets.forEach(
        (groupId, byConsumer) ->
            byConsumer.forEach(
                (consumerId, byPartition) -> {
                  final Long pos = byPartition.get(partitionId);
                  if (pos != null) {
                    result.add(new OffsetEntry(groupId, consumerId, partitionId, pos));
                  }
                }));
    return Collections.unmodifiableList(result);
  }

  /** Returns all offset entries across all partitions as an unmodifiable list. */
  public List<OffsetEntry> getAllEntries() {
    final var result = new ArrayList<OffsetEntry>();
    offsets.forEach(
        (groupId, byConsumer) ->
            byConsumer.forEach(
                (consumerId, byPartition) ->
                    byPartition.forEach(
                        (partitionId, pos) ->
                            result.add(new OffsetEntry(groupId, consumerId, partitionId, pos)))));
    return Collections.unmodifiableList(result);
  }

  // -------------------------------------------------------------------------
  // Value types

  /**
   * Identifies a {@code (groupId, consumerId)} pair. Passed to {@link #getTruncationBoundary(int,
   * Set)} to describe the set of alive, assigned consumers.
   */
  public record ConsumerKey(String groupId, String consumerId) {}

  /** A single committed-offset entry for a {@code (group, consumer, partition)} triple. */
  public record OffsetEntry(
      String groupId, String consumerId, int partitionId, long committedPosition) {}
}
