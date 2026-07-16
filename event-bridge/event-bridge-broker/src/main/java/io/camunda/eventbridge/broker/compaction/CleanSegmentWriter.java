/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.compaction;

import io.camunda.eventbridge.protocol.EventBridgeBatch;
import io.camunda.eventbridge.protocol.EventBridgeBatchBuilder;
import io.camunda.eventbridge.protocol.EventBridgeEntryBuilder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Writes surviving records into clean segments, re-wrapping each record as its own single-entry
 * {@link EventBridgeBatch} block (ADR 0001, decision 4). This is wire-format-identical to a
 * one-record producer batch, so the existing reader and consumer SDK read a clean segment with zero
 * changes; arbitrary position gaps between records are represented for free because every batch
 * carries its own absolute position.
 *
 * <h3>Determinism</h3>
 *
 * <p>For identical input records the output bytes are identical: the batch builder is
 * deterministic, position and timestamp are patched to the record's own values (both lie outside
 * the CRC range, so the CRC stays valid), and segment file names are a deterministic function of
 * first position and cleaner point. The source batch's <em>entire</em> attributes int — {@code
 * KEYED}, compression codec bits, and any future bits — is written back verbatim (with the CRC
 * recomputed, since attributes are CRC-covered): value bytes are copied raw, so the bits describing
 * them must survive the rewrap bit-identically.
 *
 * <h3>Rolling and crash safety</h3>
 *
 * <p>Records are appended to an in-memory segment buffer until adding the next batch would exceed
 * the configured segment size bound, at which point the current segment is finalized and a new one
 * started. A single record larger than the bound occupies its own oversized segment (a batch is
 * never split). Each finalized segment is written durably — temp file, fsync, atomic rename, dir
 * fsync (see {@link DurableFiles}) — so a reader never sees a partially written segment under its
 * final name. Segments written by a pass that crashes before the manifest commit are orphans: not
 * referenced by any committed manifest, they are swept on a later pass.
 *
 * <p>Threading: not thread-safe; owned by a single cleaner pass on the cleaner actor.
 */
public final class CleanSegmentWriter {

  private final Path directory;
  private final long cleanerPoint;
  private final long cleanedAtTimestamp;
  private final long maxSegmentBytes;

  private final EventBridgeBatchBuilder batchBuilder = new EventBridgeBatchBuilder();
  private final EventBridgeEntryBuilder entryBuilder = new EventBridgeEntryBuilder();
  private final List<CleanSegment> finalized = new ArrayList<>();

  private ByteArrayOutputStream currentSegment;
  private long currentFirstPosition;
  private boolean finished;

  /**
   * @param directory the compaction directory the segments are written into
   * @param cleanerPoint the cleaner point C of this pass (encoded into segment names to keep
   *     passes' outputs uniquely named)
   * @param cleanedAtTimestamp the wall-clock millis of this pass (recorded per segment)
   * @param maxSegmentBytes the soft upper bound on a segment's size in bytes (must be positive)
   */
  public CleanSegmentWriter(
      final Path directory,
      final long cleanerPoint,
      final long cleanedAtTimestamp,
      final long maxSegmentBytes) {
    if (maxSegmentBytes <= 0) {
      throw new IllegalArgumentException(
          "maxSegmentBytes must be positive, was " + maxSegmentBytes);
    }
    this.directory = directory;
    this.cleanerPoint = cleanerPoint;
    this.cleanedAtTimestamp = cleanedAtTimestamp;
    this.maxSegmentBytes = maxSegmentBytes;
  }

  /**
   * Appends a surviving record, re-wrapped as a single-entry batch, preserving its position,
   * timestamp, key, value and raw attributes int. Rolls to a new segment first if the current one
   * is non-empty and would exceed the size bound.
   *
   * @param record the record to write
   */
  public void append(final CompactionRecord record) {
    ensureNotFinished();
    final byte[] batch = buildBatch(record);

    if (currentSegment != null
        && currentSegment.size() > 0
        && (long) currentSegment.size() + batch.length > maxSegmentBytes) {
      finalizeCurrentSegment();
    }
    if (currentSegment == null) {
      currentSegment = new ByteArrayOutputStream();
      currentFirstPosition = record.position();
    }
    currentSegment.writeBytes(batch);
  }

  /**
   * Finalizes the open segment (if any) and returns the metadata of every segment written by this
   * writer, in ascending first-position order. After this call the writer must not be reused.
   *
   * @return the list of {@link CleanSegment} descriptors, durably persisted on disk
   */
  public List<CleanSegment> finish() {
    ensureNotFinished();
    finalizeCurrentSegment();
    finished = true;
    return List.copyOf(finalized);
  }

  private byte[] buildBatch(final CompactionRecord record) {
    entryBuilder.reset();
    if (record.hasKey()) {
      entryBuilder.key(record.key());
    }
    entryBuilder.value(record.value());
    final byte[] entry = entryBuilder.build();

    batchBuilder.reset();
    batchBuilder.addEntry(entry);
    final byte[] batch = batchBuilder.build();

    final var buffer = new UnsafeBuffer(batch);

    // Write the source batch's attributes int back verbatim — KEYED, compression codec bits, and
    // any future bits travel with the raw value bytes they describe. Attributes are inside the CRC
    // range, so the CRC must be recomputed afterwards.
    buffer.putInt(EventBridgeBatch.ATTRIBUTES_OFFSET, record.attributes());
    EventBridgeBatch.writeCrc(buffer, 0);

    // Patch the record's own position and timestamp back in; both are outside the CRC range so the
    // CRC just computed stays valid. The builder writes zeros there because on the producer path
    // the broker assigns them — here we preserve already-assigned values verbatim, never
    // renumbering.
    EventBridgeBatch.patchPosition(buffer, 0, record.position());
    EventBridgeBatch.patchTimestamp(buffer, 0, record.timestamp());
    return batch;
  }

  private void finalizeCurrentSegment() {
    if (currentSegment == null || currentSegment.size() == 0) {
      currentSegment = null;
      return;
    }
    final byte[] bytes = currentSegment.toByteArray();
    final String name = CleanSegmentFiles.segmentName(currentFirstPosition, cleanerPoint);
    final String tmp = CleanSegmentFiles.tmpName(currentFirstPosition, cleanerPoint);
    try {
      DurableFiles.writeAtomically(directory.resolve(name), directory.resolve(tmp), bytes);
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to write clean segment " + name, e);
    }

    final var crc = new CRC32();
    crc.update(bytes);
    finalized.add(
        new CleanSegment(
            name, currentFirstPosition, cleanedAtTimestamp, bytes.length, crc.getValue()));
    currentSegment = null;
  }

  private void ensureNotFinished() {
    if (finished) {
      throw new IllegalStateException("CleanSegmentWriter already finished");
    }
  }
}
