/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.leader;

import io.camunda.zeebe.backup.logarchive.format.LogArchiveFrame;
import io.camunda.zeebe.backup.logarchive.format.LogObjectDecoder;
import io.camunda.zeebe.backup.logarchive.format.RaftEntryInfo;
import io.camunda.zeebe.backup.logarchive.format.SegmentIdentity;
import io.camunda.zeebe.backup.logarchive.format.SegmentV1Decoder;
import io.camunda.zeebe.backup.logarchive.format.SegmentV1Encoder;
import io.camunda.zeebe.backup.logarchive.format.SegmentV1Encoder.WrittenSegment;
import io.camunda.zeebe.backup.logarchive.model.IndexChunkFile;
import io.camunda.zeebe.backup.logarchive.model.LogArchiveJson;
import io.camunda.zeebe.backup.logarchive.model.LogArchiveKeys;
import io.camunda.zeebe.backup.logarchive.model.LogObjectJson;
import io.camunda.zeebe.backup.logarchive.processing.LogArchiveListener;
import io.camunda.zeebe.backup.logarchive.state.DbLogArchiveState.UnmergedObject;
import io.camunda.zeebe.backup.logarchive.state.LogArchiveMeta;
import io.camunda.zeebe.backup.logarchive.store.LogArchiveStore;
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.logstreams.log.WriteContext;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.management.IndexChunk;
import io.camunda.zeebe.protocol.impl.record.value.management.LogArchiveRecord;
import io.camunda.zeebe.protocol.impl.record.value.management.LogObject;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.management.LogArchiveIntent;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.util.Either;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Replaces the small objects the archiver cut with one bigger object per journal segment and
 * commits the replacement with {@code MERGE_LOG} (spec section 20.2). Runs only on the partition
 * leader, one merge in flight at a time: {@code IDLE → MERGING → AWAIT_COMMIT → IDLE}.
 *
 * <p>A merge is due once a journal segment that holds entries above the merged index M is complete
 * (the journal rolled over to a newer segment) and the archived index F has passed its last index,
 * or once {@code mergeMaxObjects} objects are unmerged. It covers M + 1 up to the last object
 * boundary at or below the segment's last index, at most {@code mergeMaxObjects} objects. A run of
 * one object is proposed as it is, without uploading anything. Otherwise the entries are read from
 * the local journal, or from the run's objects in the bucket if the journal no longer holds them,
 * encoded frame by frame into a temporary file and uploaded to {@code data/<M+1>-<L>-t<term>.seg}.
 *
 * <p>The merger does nothing before the publisher's recovery check, and not on a timeline the
 * bucket is ahead of: it waits for the first {@code LOG_ARCHIVED} of a timeline the archiver
 * resumed, or for a {@code TIMELINE_STARTED}. A timeline started before merging existed has no
 * merged index ({@code mergedIndex < 0}) and is never merged.
 *
 * <p>All state lives on the actor thread. Every asynchronous continuation captures the current
 * {@code epoch} and is dropped if the merge it belongs to was abandoned in the meantime.
 */
public final class LogArchiveMerger extends Actor
    implements LogArchiveListener, LogArchiveRecoveryListener {

  private static final Logger LOG = LoggerFactory.getLogger(LogArchiveMerger.class);
  private static final Duration INITIAL_BACKOFF = Duration.ofMillis(100);
  private static final Duration MAX_BACKOFF = Duration.ofSeconds(10);
  private static final String TEMP_FILE_PREFIX = "merge-";
  private static final String TEMP_FILE_SUFFIX = ".seg.tmp";

  private final int partitionId;
  private final SegmentIdentity identity;
  private final long term;
  private final LogArchiveStore store;
  private final LogStreamWriter writer;
  private final LogArchiverEntrySource source;
  private final Supplier<List<Long>> segmentFirstIndexes;
  private final LogArchiveStateView stateView;
  private final int mergeMaxObjects;
  private final long mergeFrameBytes;
  private final Path tempDirectory;

  // all fields below are only accessed on the actor thread
  private State state = State.AWAIT_RECOVERY;
  private boolean bucketAhead;
  private @Nullable InFlight inFlight;
  // the temporary file of the merge in flight until it is uploaded
  private @Nullable Path tempFile;
  private long epoch;
  private Duration backoff = INITIAL_BACKOFF;

  /**
   * @param clusterId the cluster id written into the identity of every merged object
   * @param physicalTenantId the physical tenant id written into the identity of every merged object
   * @param term the leader's term; written into the key of every merged object
   * @param source committed entries of the local journal; owned and closed by the merger
   * @param segmentFirstIndexes the first index of each segment of the local journal, ascending
   * @param stateView read access to the replicated state, used only by this merger
   * @param mergeMaxObjects merge at most this many objects at once, and as soon as this many are
   *     unmerged
   * @param mergeFrameBytes entry bytes per zstd frame of a merged object
   * @param tempDirectory where merged objects are written before the upload; created if missing
   */
  public LogArchiveMerger(
      final int partitionId,
      final String clusterId,
      final String physicalTenantId,
      final long term,
      final LogArchiveStore store,
      final LogStreamWriter writer,
      final LogArchiverEntrySource source,
      final Supplier<List<Long>> segmentFirstIndexes,
      final LogArchiveStateView stateView,
      final int mergeMaxObjects,
      final long mergeFrameBytes,
      final Path tempDirectory) {
    super(
        buildActorName("LogArchiveMerger", partitionId),
        null,
        Map.of(ACTOR_PROP_PARTITION_ID, Integer.toString(partitionId)));
    if (mergeMaxObjects <= 0) {
      throw new IllegalArgumentException(
          "mergeMaxObjects must be positive, but was " + mergeMaxObjects);
    }
    if (mergeFrameBytes <= 0) {
      throw new IllegalArgumentException(
          "mergeFrameBytes must be positive, but was " + mergeFrameBytes);
    }
    this.partitionId = partitionId;
    identity = new SegmentIdentity(clusterId, physicalTenantId, partitionId);
    this.term = term;
    this.store = store;
    this.writer = writer;
    this.source = source;
    this.segmentFirstIndexes = segmentFirstIndexes;
    this.stateView = stateView;
    this.mergeMaxObjects = mergeMaxObjects;
    this.mergeFrameBytes = mergeFrameBytes;
    this.tempDirectory = tempDirectory;
  }

  @Override
  protected void onActorStarted() {
    deleteTempFiles();
  }

  @Override
  protected void onActorClosing() {
    state = State.CLOSED;
    abandon();
    source.close();
  }

  // -----------------------------------------------------------------------------------------------
  // Inputs from other threads
  // -----------------------------------------------------------------------------------------------

  @Override
  public void onRecoveryChecked(final LogArchiveMeta meta, final boolean recoveredBehindBucket) {
    actor.run(
        () -> {
          if (state == State.CLOSED) {
            return;
          }
          abandon();
          bucketAhead = recoveredBehindBucket;
          state = State.AWAIT_TIMELINE;
        });
  }

  @Override
  public void onTimelineStarted(final String timelineId) {
    actor.run(
        () -> {
          if (state == State.CLOSED || state == State.AWAIT_RECOVERY) {
            return;
          }
          abandon();
          bucketAhead = false;
          backoff = INITIAL_BACKOFF;
          state = State.IDLE;
          tryMerge();
        });
  }

  @Override
  public void onLogArchived(final long archivedIndex, final long archivedPosition) {
    actor.run(
        () -> {
          if (state == State.AWAIT_TIMELINE && !bucketAhead) {
            // the archiver resumed the recovered timeline: it continues it in the bucket
            state = State.IDLE;
          }
          tryMerge();
        });
  }

  @Override
  public void onLogMerged(final long mergedIndex, final long mergedPosition) {
    actor.run(() -> logMerged(mergedIndex));
  }

  @Override
  public void onMergeLogRejected(final long mergedIndex) {
    actor.run(() -> mergeLogRejected(mergedIndex));
  }

  // -----------------------------------------------------------------------------------------------
  // State machine (actor thread)
  // -----------------------------------------------------------------------------------------------

  private void tryMerge() {
    if (state != State.IDLE) {
      return;
    }
    final LogArchiveMeta meta;
    final List<UnmergedObject> run;
    try {
      meta = stateView.meta();
      if (meta.getTimelineId().isEmpty() || meta.getMergedIndex() < 0 || meta.isAuthority()) {
        // no timeline yet, one started before merging existed, or one the authority compacts
        return;
      }
      run =
          chooseRun(
              meta.getMergedIndex(),
              meta.getArchivedIndex(),
              stateView.unmergedObjects(),
              segmentFirstIndexes.get(),
              mergeMaxObjects);
    } catch (final RuntimeException e) {
      fail("read the state to find the next merge", e);
      return;
    }
    if (run.isEmpty()) {
      return;
    }

    state = State.MERGING;
    final var timelineId = meta.getTimelineId();
    if (run.size() == 1) {
      // nothing to replace: the archived object itself becomes the merged one
      whenResolved(timelineId, run, objects -> propose(timelineId, objects.getFirst()));
      return;
    }
    final long firstIndex = run.getFirst().firstIndex();
    final long lastIndex = run.getLast().lastIndex();
    LOG.debug(
        "Partition {}: merging {} log objects [{}, {}] of timeline {}",
        partitionId,
        run.size(),
        firstIndex,
        lastIndex,
        timelineId);
    if (source.firstIndex() <= firstIndex) {
      mergeFromJournal(timelineId, firstIndex, lastIndex);
    } else {
      LOG.info(
          "Partition {}: the journal starts after index {}; merging [{}, {}] from the bucket",
          partitionId,
          firstIndex,
          firstIndex,
          lastIndex);
      whenResolved(timelineId, run, objects -> mergeFromBucket(timelineId, objects));
    }
  }

  /**
   * Picks the next run of unmerged objects to merge, empty if none is due.
   *
   * @param mergedIndex M
   * @param archivedIndex F
   * @param unmerged every archived object above M, in index order
   * @param segmentFirstIndexes the first index of each journal segment, ascending; every segment
   *     but the last is complete
   */
  static List<UnmergedObject> chooseRun(
      final long mergedIndex,
      final long archivedIndex,
      final List<UnmergedObject> unmerged,
      final List<Long> segmentFirstIndexes,
      final int mergeMaxObjects) {
    if (unmerged.isEmpty() || unmerged.getFirst().firstIndex() != mergedIndex + 1) {
      return List.of();
    }
    for (int i = 0; i + 1 < segmentFirstIndexes.size(); i++) {
      final long segmentLastIndex = segmentFirstIndexes.get(i + 1) - 1;
      if (segmentLastIndex <= mergedIndex) {
        continue;
      }
      if (segmentLastIndex > archivedIndex) {
        break;
      }
      // the last object boundary at or below the segment's end; an object that spans into the
      // next segment is merged with that one
      int count = 0;
      while (count < unmerged.size() && unmerged.get(count).lastIndex() <= segmentLastIndex) {
        count++;
      }
      if (count > 0) {
        return unmerged.subList(0, Math.min(count, mergeMaxObjects));
      }
    }
    if (unmerged.size() >= mergeMaxObjects) {
      return unmerged.subList(0, mergeMaxObjects);
    }
    return List.of();
  }

  private void mergeFromJournal(final String timelineId, final long firstIndex, final long last) {
    final Path file;
    final WrittenSegment written;
    try {
      file = newTempFile();
    } catch (final IOException | RuntimeException e) {
      fail("create a temporary file for the merge", e);
      return;
    }
    try {
      source.seek(firstIndex);
      try (final var out = new BufferedOutputStream(Files.newOutputStream(file))) {
        final var segment = SegmentV1Encoder.writer(out, identity, timelineId, mergeFrameBytes);
        long latestTimestamp = -1;
        for (long index = firstIndex; index <= last; index++) {
          if (!source.hasNext()) {
            throw new IllegalStateException(
                "the committed journal ends before index %d of the merge".formatted(index));
          }
          final var entry = source.next();
          if (entry.frame().index() != index) {
            throw new IllegalStateException(
                "expected to read index %d from the journal, but got %d"
                    .formatted(index, entry.frame().index()));
          }
          segment.append(entry.frame());
          latestTimestamp = Math.max(latestTimestamp, entry.latestTimestamp());
        }
        written = segment.finish(latestTimestamp);
      }
    } catch (final IOException | RuntimeException e) {
      deleteQuietly(file);
      fail("encode the merge of [%d, %d]".formatted(firstIndex, last), e);
      return;
    }
    upload(timelineId, written, file);
  }

  /**
   * Rebuilds the merged object from the run's objects in the bucket: the same entries, so the same
   * bytes as from the journal. Objects are downloaded one after the other, so that only one is in
   * memory at a time.
   */
  private void mergeFromBucket(final String timelineId, final List<LogObject> objects) {
    final Path file;
    final BufferedOutputStream out;
    try {
      file = newTempFile();
      out = new BufferedOutputStream(Files.newOutputStream(file));
    } catch (final IOException | RuntimeException e) {
      fail("create a temporary file for the merge", e);
      return;
    }
    final var segment = SegmentV1Encoder.writer(out, identity, timelineId, mergeFrameBytes);
    final long latestTimestamp =
        objects.stream().mapToLong(LogObject::getLastRecordTimestamp).max().orElse(-1);
    appendFromBucket(timelineId, objects, 0, segment, out, file, latestTimestamp);
  }

  private void appendFromBucket(
      final String timelineId,
      final List<LogObject> objects,
      final int next,
      final SegmentV1Encoder.Writer segment,
      final BufferedOutputStream out,
      final Path file,
      final long latestTimestamp) {
    if (next == objects.size()) {
      final WrittenSegment written;
      try (out) {
        written = segment.finish(latestTimestamp);
      } catch (final IOException | RuntimeException e) {
        deleteQuietly(file);
        fail("encode the merge from the bucket", e);
        return;
      }
      upload(timelineId, written, file);
      return;
    }
    final var object = objects.get(next);
    final var key = LogArchiveKeys.partitionPrefix(timelineId, partitionId) + object.getKey();
    final long expectedEpoch = epoch;
    getIfExists(key)
        .whenComplete(
            (content, error) ->
                actor.run(
                    () -> {
                      if (expectedEpoch != epoch || state != State.MERGING) {
                        closeQuietly(out);
                        return;
                      }
                      try {
                        if (error != null) {
                          throw new IllegalStateException("failed to read " + key, error);
                        }
                        final byte[] bytes =
                            content.orElseThrow(
                                () -> new IllegalStateException("log object not found: " + key));
                        if (!sha256(bytes).equals(object.getSha256())) {
                          throw new IllegalStateException(
                              "log object differs from its sha256: " + key);
                        }
                        for (final var frame : decode(object.getKey(), bytes)) {
                          segment.append(frame);
                        }
                      } catch (final RuntimeException e) {
                        closeQuietly(out);
                        deleteQuietly(file);
                        fail("merge from the bucket", e);
                        return;
                      }
                      appendFromBucket(
                          timelineId, objects, next + 1, segment, out, file, latestTimestamp);
                    }));
  }

  /** Decodes a log object of either format; segment frames get the positions of their entry. */
  private static List<LogArchiveFrame> decode(final String key, final byte[] bytes) {
    if (!LogArchiveKeys.isSegmentKey(key)) {
      return LogObjectDecoder.decode(bytes).frames();
    }
    final var frames = new ArrayList<LogArchiveFrame>();
    for (final var frame : SegmentV1Decoder.decode(bytes).frames()) {
      if (frame.asqn() < 0) {
        frames.add(frame);
        continue;
      }
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
    return frames;
  }

  private void upload(final String timelineId, final WrittenSegment written, final Path file) {
    final var footer = written.footer();
    final var relativeKey = LogArchiveKeys.dataKey(footer.firstIndex(), footer.lastIndex(), term);
    final var object =
        new LogObject()
            .setKey(relativeKey)
            .setTerm(term)
            .setFirstIndex(footer.firstIndex())
            .setLastIndex(footer.lastIndex())
            .setFirstPosition(footer.firstPosition())
            .setLastPosition(footer.lastPosition())
            .setEntryCount(footer.entryCount())
            .setSizeBytes(written.sizeBytes())
            .setSha256(written.sha256())
            .setBodyLength(footer.bodyLength())
            .setLastRecordTimestamp(footer.latestTimestamp());
    inFlight = new InFlight(timelineId, object, file);
    putFile();
  }

  private void putFile() {
    final var current = inFlight;
    final var file = current != null ? current.file() : null;
    if (state != State.MERGING || current == null || file == null) {
      return;
    }
    final var key =
        LogArchiveKeys.partitionPrefix(current.timelineId(), partitionId)
            + current.object().getKey();
    final long expectedEpoch = epoch;
    CompletableFuture<Void> future;
    try {
      future = store.putFile(key, file);
    } catch (final RuntimeException e) {
      future = CompletableFuture.failedFuture(e);
    }
    future.whenComplete(
        (ignored, error) ->
            actor.run(
                () -> {
                  if (expectedEpoch != epoch || state != State.MERGING) {
                    return;
                  }
                  if (error != null) {
                    LOG.warn(
                        "Partition {}: failed to upload merged log object {}, retrying in {}",
                        partitionId,
                        key,
                        backoff,
                        error);
                    retryLater(this::putFile);
                    return;
                  }
                  deleteQuietly(file);
                  tempFile = null;
                  propose(current.timelineId(), current.object());
                }));
  }

  private void propose(final String timelineId, final LogObject object) {
    inFlight = new InFlight(timelineId, object, null);
    state = State.AWAIT_COMMIT;
    writeMergeLog();
  }

  private void writeMergeLog() {
    final var current = inFlight;
    if (state != State.AWAIT_COMMIT || current == null) {
      return;
    }
    final LogArchiveMeta meta;
    try {
      meta = stateView.meta();
    } catch (final RuntimeException e) {
      LOG.warn("Partition {}: failed to read the state, retrying in {}", partitionId, backoff, e);
      retryLater(this::writeMergeLog);
      return;
    }
    if (!meta.getTimelineId().equals(current.timelineId())
        || meta.getMergedIndex() + 1 != current.object().getFirstIndex()) {
      // M moved while merging (a merge of an earlier term was committed); this one no longer
      // continues M and would be rejected; the uploaded object becomes an orphan for the cleaner
      inFlight = null;
      state = State.IDLE;
      tryMerge();
      return;
    }
    final var record =
        new LogArchiveRecord().setTimelineId(current.timelineId()).addObject(current.object());
    if (!write(record)) {
      retryLater(this::writeMergeLog);
    }
  }

  private void logMerged(final long mergedIndex) {
    final var current = inFlight;
    if (state == State.AWAIT_COMMIT
        && current != null
        && current.object().getLastIndex() == mergedIndex) {
      LOG.debug(
          "Partition {}: merged log objects up to index {} into {}",
          partitionId,
          mergedIndex,
          current.object().getKey());
      inFlight = null;
      backoff = INITIAL_BACKOFF;
      state = State.IDLE;
    }
    tryMerge();
  }

  private void mergeLogRejected(final long mergedIndex) {
    if (state != State.AWAIT_COMMIT) {
      return;
    }
    LOG.debug(
        "Partition {}: MERGE_LOG rejected at merged index {}, looking for the next merge in {}",
        partitionId,
        mergedIndex,
        backoff);
    inFlight = null;
    state = State.BACKOFF;
    retryLater(this::resume);
  }

  private void resume() {
    if (state == State.BACKOFF) {
      state = State.IDLE;
      tryMerge();
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Resolving the archived objects of a run
  // -----------------------------------------------------------------------------------------------

  /**
   * Looks up the archived objects of a run: from the open objects in the state, or, once sealed,
   * from their chunk in the bucket. A run's rows hold only index ranges; the committed object of a
   * range must be the one the commit entries name, not another one with the same range.
   */
  private void whenResolved(
      final String timelineId,
      final List<UnmergedObject> run,
      final Consumer<List<LogObject>> then) {
    final long expectedEpoch = epoch;
    CompletableFuture<List<LogObject>> future;
    try {
      future = resolve(timelineId, run);
    } catch (final RuntimeException e) {
      future = CompletableFuture.failedFuture(e);
    }
    future.whenComplete(
        (objects, error) ->
            actor.run(
                () -> {
                  if (expectedEpoch != epoch || state != State.MERGING) {
                    return;
                  }
                  if (error != null) {
                    fail("look up the archived objects of the merge", error);
                    return;
                  }
                  then.accept(objects);
                }));
  }

  private CompletableFuture<List<LogObject>> resolve(
      final String timelineId, final List<UnmergedObject> run) {
    final Map<Long, LogObject> found = new HashMap<>();
    stateView
        .openObjects()
        .forEach(open -> found.put(open.object().getFirstIndex(), open.object()));
    final Map<String, IndexChunk> chunksToRead = new LinkedHashMap<>();
    final var chunks = stateView.chunks();
    for (final var row : run) {
      if (found.containsKey(row.firstIndex())) {
        continue;
      }
      final var chunk =
          chunks.stream()
              .filter(
                  c ->
                      c.getFirstIndex() <= row.firstIndex() && row.firstIndex() <= c.getLastIndex())
              .findFirst()
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "no open object and no chunk holds the object at index "
                              + row.firstIndex()));
      chunksToRead.putIfAbsent(chunk.getKey(), chunk);
    }

    final var prefix = LogArchiveKeys.partitionPrefix(timelineId, partitionId);
    final List<CompletableFuture<List<LogObjectJson>>> reads = new ArrayList<>();
    for (final var chunkKey : chunksToRead.keySet()) {
      reads.add(
          getIfExists(prefix + chunkKey)
              .thenApply(
                  content ->
                      LogArchiveJson.read(
                              content.orElseThrow(
                                  () ->
                                      new IllegalStateException(
                                          "index chunk not found: " + prefix + chunkKey)),
                              IndexChunkFile.class)
                          .objects()));
    }
    return CompletableFuture.allOf(reads.toArray(CompletableFuture[]::new))
        .thenApply(
            ignored -> {
              final Map<Long, LogObject> all = new HashMap<>(found);
              reads.forEach(
                  read ->
                      read.join()
                          .forEach(json -> all.putIfAbsent(json.firstIndex(), toLogObject(json))));
              final List<LogObject> objects = new ArrayList<>(run.size());
              for (final var row : run) {
                final var object = all.get(row.firstIndex());
                if (object == null || object.getLastIndex() != row.lastIndex()) {
                  throw new IllegalStateException(
                      "no archived object [%d, %d] found"
                          .formatted(row.firstIndex(), row.lastIndex()));
                }
                objects.add(object);
              }
              return objects;
            });
  }

  private static LogObject toLogObject(final LogObjectJson json) {
    return new LogObject()
        .setKey(json.key())
        .setTerm(json.term())
        .setFirstIndex(json.firstIndex())
        .setLastIndex(json.lastIndex())
        .setFirstPosition(json.firstPosition())
        .setLastPosition(json.lastPosition())
        .setEntryCount(json.entryCount())
        .setSizeBytes(json.sizeBytes())
        .setSha256(json.sha256())
        .setBodyLength(json.bodyLength())
        .setLastRecordTimestamp(json.lastRecordTimestamp());
  }

  // -----------------------------------------------------------------------------------------------
  // Helpers
  // -----------------------------------------------------------------------------------------------

  private CompletableFuture<Optional<byte[]>> getIfExists(final String key) {
    try {
      return store.getIfExists(key);
    } catch (final RuntimeException e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  private boolean write(final LogArchiveRecord record) {
    final var result =
        writer.tryWrite(
            WriteContext.internal(),
            LogAppendEntry.of(
                new RecordMetadata()
                    .recordType(RecordType.COMMAND)
                    .valueType(ValueType.LOG_ARCHIVE)
                    .intent(LogArchiveIntent.MERGE_LOG),
                record));
    if (result instanceof Either.Left<?, ?>(final var failure)) {
      LOG.warn(
          "Partition {}: failed to write MERGE_LOG ({}), retrying in {}",
          partitionId,
          failure,
          backoff);
      return false;
    }
    return true;
  }

  /** Gives up the current merge and looks for the next one after a backoff. */
  private void fail(final String what, final Throwable error) {
    LOG.warn("Partition {}: failed to {}, retrying in {}", partitionId, what, backoff, error);
    abandon();
    state = State.BACKOFF;
    retryLater(this::resume);
  }

  /** Drops the merge in flight, if any, and every continuation that belongs to it. */
  private void abandon() {
    epoch++;
    final var file = tempFile;
    if (file != null) {
      deleteQuietly(file);
    }
    tempFile = null;
    inFlight = null;
  }

  private void retryLater(final Runnable action) {
    final long expectedEpoch = epoch;
    final long delayMs = backoff.toMillis();
    final var doubled = backoff.multipliedBy(2);
    backoff = doubled.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : doubled;
    actor.schedule(
        Duration.ofMillis(delayMs),
        () -> {
          if (expectedEpoch == epoch && state != State.CLOSED) {
            action.run();
          }
        });
  }

  private Path newTempFile() throws IOException {
    Files.createDirectories(tempDirectory);
    final var file = Files.createTempFile(tempDirectory, TEMP_FILE_PREFIX, TEMP_FILE_SUFFIX);
    tempFile = file;
    return file;
  }

  /** Removes the temporary files a previous leader left behind, e.g. when it crashed. */
  private void deleteTempFiles() {
    if (!Files.isDirectory(tempDirectory)) {
      return;
    }
    try (final DirectoryStream<Path> files =
        Files.newDirectoryStream(tempDirectory, TEMP_FILE_PREFIX + "*" + TEMP_FILE_SUFFIX)) {
      files.forEach(LogArchiveMerger::deleteQuietly);
    } catch (final IOException e) {
      LOG.warn("Partition {}: failed to list {}", partitionId, tempDirectory, e);
    }
  }

  private static String sha256(final byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (final NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static void deleteQuietly(final Path file) {
    try {
      Files.deleteIfExists(file);
    } catch (final IOException e) {
      LOG.warn("Failed to delete temporary file {}", file, e);
    }
  }

  private static void closeQuietly(final BufferedOutputStream out) {
    try {
      out.close();
    } catch (final IOException e) {
      LOG.debug("Failed to close a temporary file", e);
    }
  }

  /** The state of the merger; visible for tests. */
  State state() {
    return state;
  }

  enum State {
    /** Waiting for the publisher's recovery check. */
    AWAIT_RECOVERY,
    /**
     * Waiting until the timeline in the state is the one the archiver continues in the bucket: the
     * first {@code LOG_ARCHIVED} after a recovery the bucket is not ahead of, or a {@code
     * TIMELINE_STARTED}.
     */
    AWAIT_TIMELINE,
    IDLE,
    /** Looking up, reading, encoding or uploading the merge in flight. */
    MERGING,
    /** MERGE_LOG written (or about to be), waiting for LOG_MERGED or the rejection. */
    AWAIT_COMMIT,
    /** The next merge is looked for after a backoff. */
    BACKOFF,
    CLOSED
  }

  /**
   * @param file the temporary file of a merged object that is not uploaded yet, else null
   */
  private record InFlight(String timelineId, LogObject object, @Nullable Path file) {}
}
