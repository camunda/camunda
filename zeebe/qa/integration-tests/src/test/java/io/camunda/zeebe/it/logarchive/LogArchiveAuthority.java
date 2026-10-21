/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.logarchive;

import io.camunda.zeebe.backup.logarchive.format.LogArchiveFrame;
import io.camunda.zeebe.backup.logarchive.format.RaftEntryInfo;
import io.camunda.zeebe.backup.logarchive.format.SegmentV1Decoder;
import io.camunda.zeebe.backup.logarchive.format.SegmentV1Encoder;
import io.camunda.zeebe.backup.logarchive.model.LogArchiveJson;
import io.camunda.zeebe.backup.logarchive.model.LogArchiveKeys;
import io.camunda.zeebe.backup.logarchive.model.LogObjectJson;
import io.camunda.zeebe.backup.logarchive.model.MaintenanceEntry;
import io.camunda.zeebe.backup.logarchive.model.MaintenanceEntry.Type;
import io.camunda.zeebe.backup.logarchive.store.LogArchiveStore;
import io.camunda.zeebe.restore.logarchive.LogArchiveView;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Plays the authority of a timeline in mode B (spec section 25.3) for the tests: compacts runs of
 * archived objects into one multi-frame object, writes the maintenance entries and deletes the
 * objects they replace or remove. The brokers writing to the archive must be stopped.
 */
final class LogArchiveAuthority {

  /** Small enough that a compacted object has several frames. */
  private static final long FRAME_BYTES = 2 * 1024;

  private final LogArchiveStore store;
  private final Path partitionDirectory;
  private final String timelineId;
  private final int partitionId;
  private final String prefix;

  LogArchiveAuthority(
      final LogArchiveStore store,
      final Path partitionDirectory,
      final String timelineId,
      final int partitionId) {
    this.store = store;
    this.partitionDirectory = partitionDirectory;
    this.timelineId = timelineId;
    this.partitionId = partitionId;
    prefix = LogArchiveKeys.partitionPrefix(timelineId, partitionId);
  }

  /**
   * Re-encodes the entries of the contiguous inputs into one object {@code
   * data/<first>-<last>-c<seq>.seg}, writes it and the {@code COMPACTED} entry, then deletes the
   * inputs.
   *
   * @return the compacted object
   */
  LogObjectJson compact(
      final long seq, final long basedOnCommitSeq, final List<LogObjectJson> inputs)
      throws IOException {
    final var frames = new ArrayList<LogArchiveFrame>();
    long latestTimestamp = -1;
    long term = 0;
    io.camunda.zeebe.backup.logarchive.format.SegmentIdentity identity = null;
    for (final var input : inputs) {
      final var segment =
          SegmentV1Decoder.decode(store.getIfExists(prefix + input.key()).join().orElseThrow());
      identity = segment.identity();
      latestTimestamp = Math.max(latestTimestamp, segment.footer().latestTimestamp());
      term = Math.max(term, input.term());
      for (final var frame : segment.frames()) {
        final var info = RaftEntryInfo.read(frame.serializedRecord());
        frames.add(
            new LogArchiveFrame(
                frame.index(),
                info.term(),
                frame.asqn(),
                info.lowestPosition(),
                info.highestPosition(),
                frame.checksum(),
                frame.serializedRecord()));
      }
    }

    final var out = new ByteArrayOutputStream();
    final var writer = SegmentV1Encoder.writer(out, identity, timelineId, FRAME_BYTES);
    frames.forEach(writer::append);
    final var written = writer.finish(latestTimestamp);
    final var footer = written.footer();
    final var object =
        new LogObjectJson(
            LogArchiveKeys.compactedDataKey(footer.firstIndex(), footer.lastIndex(), seq),
            term,
            footer.firstIndex(),
            footer.lastIndex(),
            footer.firstPosition(),
            footer.lastPosition(),
            frames.size(),
            written.sizeBytes(),
            written.sha256(),
            footer.bodyLength(),
            footer.latestTimestamp());
    if (written.frameCount() < 2) {
      throw new IllegalStateException("expected a multi-frame object, but got one frame");
    }

    store.put(prefix + object.key(), out.toByteArray()).join();
    writeEntry(
        new MaintenanceEntry(
            1, timelineId, partitionId, seq, Type.COMPACTED, basedOnCommitSeq, object, null, null));
    for (final var input : inputs) {
      delete(input);
    }
    return object;
  }

  /** Writes the {@code REMOVED} entry up to the end of the given object and deletes the objects. */
  void remove(final long seq, final long basedOnCommitSeq, final List<LogObjectJson> removed)
      throws IOException {
    final var last = removed.getLast();
    removeEntry(seq, basedOnCommitSeq, last.lastIndex(), last.lastPosition());
    for (final var object : removed) {
      delete(object);
    }
  }

  /** Writes a {@code REMOVED} entry only, as it is, valid or not. */
  void removeEntry(
      final long seq, final long basedOnCommitSeq, final long upToIndex, final long upToPosition) {
    writeEntry(
        new MaintenanceEntry(
            1,
            timelineId,
            partitionId,
            seq,
            Type.REMOVED,
            basedOnCommitSeq,
            null,
            upToIndex,
            upToPosition));
  }

  /** Writes a {@code COMPACTED} entry only, as it is, valid or not. */
  void compactedEntry(final long seq, final long basedOnCommitSeq, final LogObjectJson object) {
    writeEntry(
        new MaintenanceEntry(
            1, timelineId, partitionId, seq, Type.COMPACTED, basedOnCommitSeq, object, null, null));
  }

  /** Deletes an entry again, so that a test can write a different one under the same seq. */
  void deleteEntry(final long seq) throws IOException {
    Files.delete(partitionDirectory.resolve(LogArchiveKeys.maintenanceKey(seq)));
  }

  /** The objects of the view, in index order. */
  static List<LogObjectJson> objects(final LogArchiveView view) {
    return LogArchiveBucket.objects(view);
  }

  private void writeEntry(final MaintenanceEntry entry) {
    store
        .put(prefix + LogArchiveKeys.maintenanceKey(entry.seq()), LogArchiveJson.write(entry))
        .join();
  }

  private void delete(final LogObjectJson object) throws IOException {
    Files.delete(partitionDirectory.resolve(object.key()));
  }
}
