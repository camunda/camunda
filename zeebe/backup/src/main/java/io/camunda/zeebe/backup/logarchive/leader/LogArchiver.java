/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.leader;

import io.camunda.zeebe.backup.logarchive.format.EncodedLogObject;
import io.camunda.zeebe.backup.logarchive.format.LogArchiveFrame;
import io.camunda.zeebe.backup.logarchive.format.LogObjectEncoder;
import io.camunda.zeebe.backup.logarchive.model.LogArchiveKeys;
import io.camunda.zeebe.backup.logarchive.processing.LogArchiveListener;
import io.camunda.zeebe.backup.logarchive.state.LogArchiveMeta;
import io.camunda.zeebe.backup.logarchive.store.LogArchiveStore;
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.logstreams.log.WriteContext;
import io.camunda.zeebe.logstreams.storage.LogStorage.CommitListener;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.management.LogArchiveRecord;
import io.camunda.zeebe.protocol.impl.record.value.management.LogObject;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.management.LogArchiveIntent;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.clock.ActorClock;
import io.camunda.zeebe.util.Either;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Uploads committed journal entries as log objects and commits each upload with {@code ARCHIVE_LOG}
 * (spec section 8.1). Runs only on the partition leader, one object in flight at a time: {@code
 * IDLE → READING → UPLOADING → AWAIT_COMMIT → IDLE}.
 *
 * <p>Nothing happens before {@link #onRecovered(LogArchiveMeta)}: it delivers the replicated F,
 * from which the in-memory cursor {@code A} starts, or reveals that the timeline must be started
 * first.
 *
 * <p>All state lives on the actor thread. Listener callbacks and commit wake-ups arrive on other
 * threads and hop onto the actor. Every asynchronous continuation (store PUT, retry timer) captures
 * the current {@code epoch} and is dropped if the cursor was reset in the meantime, so a late
 * completion can never act on a stale range.
 */
public final class LogArchiver extends Actor implements LogArchiveListener, CommitListener {

  private static final Logger LOG = LoggerFactory.getLogger(LogArchiver.class);
  private static final Duration INITIAL_BACKOFF = Duration.ofMillis(100);
  private static final Duration MAX_BACKOFF = Duration.ofSeconds(10);
  // fixed part of a frame in the log object format: six longs and the record length
  private static final int FRAME_OVERHEAD_BYTES = 6 * Long.BYTES + Integer.BYTES;

  private final int partitionId;
  private final long term;
  private final LogArchiveStore store;
  private final LogStreamWriter writer;
  private final LogArchiverEntrySource source;
  private final Supplier<String> timelineResolver;
  private final long cutMaxBytes;
  private final Duration cutInterval;
  private final AtomicBoolean wakeUpScheduled = new AtomicBoolean();

  // all fields below are only accessed on the actor thread
  private State state = State.AWAIT_RECOVERY;
  private String timelineId = "";
  private String prefix = "";
  private long startIndex = -1;
  private String parentTimelineId = "";
  // A: the last index known to be archived (F as far as this archiver knows)
  private long archivedIndex = -1;
  private long nextReadIndex = -1;
  private final List<LogArchiveFrame> pending = new ArrayList<>();
  private long pendingBytes;
  private @Nullable InFlight inFlight;
  private long lastCutMillis;
  private long epoch;
  private Duration backoff = INITIAL_BACKOFF;

  /**
   * @param term the leader's term; written into the object key and header
   * @param source committed entries; owned and closed by the archiver
   * @param timelineResolver returns the effective timeline (timeline file, else configuration);
   *     called on the actor when recovering
   * @param cutMaxBytes cut an object once the pending entries reach this size
   * @param cutInterval cut an object at least this often while entries are pending
   */
  public LogArchiver(
      final int partitionId,
      final long term,
      final LogArchiveStore store,
      final LogStreamWriter writer,
      final LogArchiverEntrySource source,
      final Supplier<String> timelineResolver,
      final long cutMaxBytes,
      final Duration cutInterval) {
    super(
        buildActorName("LogArchiver", partitionId),
        null,
        Map.of(ACTOR_PROP_PARTITION_ID, Integer.toString(partitionId)));
    if (cutMaxBytes <= 0) {
      throw new IllegalArgumentException("cutMaxBytes must be positive, but was " + cutMaxBytes);
    }
    if (cutInterval.isNegative() || cutInterval.isZero()) {
      throw new IllegalArgumentException("cutInterval must be positive, but was " + cutInterval);
    }
    this.partitionId = partitionId;
    this.term = term;
    this.store = store;
    this.writer = writer;
    this.source = source;
    this.timelineResolver = timelineResolver;
    this.cutMaxBytes = cutMaxBytes;
    this.cutInterval = cutInterval;
  }

  @Override
  protected void onActorStarted() {
    actor.runAtFixedRate(cutInterval, this::tryCut);
  }

  @Override
  protected void onActorClosing() {
    state = State.CLOSED;
    epoch++;
    pending.clear();
    inFlight = null;
    source.close();
  }

  // -----------------------------------------------------------------------------------------------
  // Inputs from other threads
  // -----------------------------------------------------------------------------------------------

  /** Commit wake-up from the log storage. Coalesced: at most one wake-up is queued at a time. */
  @Override
  public void onCommit() {
    if (wakeUpScheduled.compareAndSet(false, true)) {
      actor.run(
          () -> {
            wakeUpScheduled.set(false);
            tryCut();
          });
    }
  }

  @Override
  public void onRecovered(final LogArchiveMeta meta) {
    actor.run(() -> recover(meta));
  }

  @Override
  public void onTimelineStarted(final String startedTimelineId) {
    actor.run(
        () -> {
          if (state == State.AWAIT_TIMELINE && startedTimelineId.equals(timelineId)) {
            LOG.info(
                "Partition {}: started log archive timeline {} at index {}",
                partitionId,
                timelineId,
                startIndex);
            resetTo(startIndex - 1);
          }
        });
  }

  @Override
  public void onLogArchived(final long newArchivedIndex, final long archivedPosition) {
    actor.run(() -> logArchived(newArchivedIndex));
  }

  @Override
  public void onArchiveLogRejected(final long currentArchivedIndex) {
    actor.run(() -> archiveLogRejected(currentArchivedIndex));
  }

  @Override
  public void onCommandRejected(
      final LogArchiveIntent command, final RejectionType type, final String reason) {
    if (command != LogArchiveIntent.START_TIMELINE) {
      return;
    }
    actor.run(
        () -> {
          if (state == State.AWAIT_TIMELINE) {
            LOG.warn(
                "Partition {}: START_TIMELINE {} was rejected ({}: {}), retrying in {}",
                partitionId,
                timelineId,
                type,
                reason,
                backoff);
            retryLater(this::writeStartTimeline);
          }
        });
  }

  // -----------------------------------------------------------------------------------------------
  // State machine (actor thread)
  // -----------------------------------------------------------------------------------------------

  private void recover(final LogArchiveMeta meta) {
    if (state == State.CLOSED) {
      return;
    }
    epoch++;
    inFlight = null;
    clearPending();
    timelineId = timelineResolver.get();
    prefix = LogArchiveKeys.partitionPrefix(timelineId, partitionId);
    backoff = INITIAL_BACKOFF;

    if (!meta.getTimelineId().equals(timelineId)) {
      startIndex = source.firstIndex();
      LOG.info(
          "Partition {}: log archive timeline is '{}', but the effective timeline is '{}'; starting it at index {}",
          partitionId,
          meta.getTimelineId(),
          timelineId,
          startIndex);
      state = State.AWAIT_TIMELINE;
      writeStartTimeline(meta.getTimelineId());
    } else {
      LOG.info(
          "Partition {}: resuming log archive timeline {} after index {}",
          partitionId,
          timelineId,
          meta.getArchivedIndex());
      resetTo(meta.getArchivedIndex());
    }
  }

  private void writeStartTimeline(final String parent) {
    parentTimelineId = parent;
    writeStartTimeline();
  }

  private void writeStartTimeline() {
    if (state != State.AWAIT_TIMELINE) {
      return;
    }
    final var record =
        new LogArchiveRecord()
            .setTimelineId(timelineId)
            .setStartIndex(startIndex)
            .setParentTimelineId(parentTimelineId);
    if (!write(LogArchiveIntent.START_TIMELINE, record)) {
      retryLater(this::writeStartTimeline);
    }
  }

  /** Discards everything read so far and continues after {@code newArchivedIndex}. */
  private void resetTo(final long newArchivedIndex) {
    epoch++;
    inFlight = null;
    clearPending();
    archivedIndex = newArchivedIndex;
    nextReadIndex = newArchivedIndex + 1;
    source.seek(nextReadIndex);
    lastCutMillis = ActorClock.currentTimeMillis();
    state = State.IDLE;
    tryCut();
  }

  private void tryCut() {
    if (state != State.IDLE) {
      return;
    }
    state = State.READING;
    while (pendingBytes < cutMaxBytes && source.hasNext()) {
      final var frame = source.next();
      if (frame.index() != nextReadIndex) {
        LOG.error(
            "Partition {}: expected to read index {} from the committed log, but got {}; retrying in {}",
            partitionId,
            nextReadIndex,
            frame.index(),
            backoff);
        clearPending();
        state = State.BACKOFF;
        final long resumeAfter = archivedIndex;
        retryLater(() -> resetTo(resumeAfter));
        return;
      }
      pending.add(frame);
      pendingBytes += FRAME_OVERHEAD_BYTES + frame.serializedRecord().length;
      nextReadIndex++;
    }

    final boolean sizeReached = pendingBytes >= cutMaxBytes;
    final boolean intervalElapsed =
        ActorClock.currentTimeMillis() - lastCutMillis >= cutInterval.toMillis();
    if (pending.isEmpty() || !(sizeReached || intervalElapsed)) {
      state = State.IDLE;
      return;
    }
    cut();
  }

  private void cut() {
    final EncodedLogObject encoded;
    try {
      encoded = LogObjectEncoder.encode(timelineId, partitionId, term, pending);
    } catch (final RuntimeException e) {
      LOG.error(
          "Partition {}: failed to encode log object for indexes [{}, {}]; retrying in {}",
          partitionId,
          pending.getFirst().index(),
          pending.getLast().index(),
          backoff,
          e);
      final long resumeAfter = archivedIndex;
      clearPending();
      state = State.BACKOFF;
      retryLater(() -> resetTo(resumeAfter));
      return;
    }
    final var header = encoded.header();
    final var relativeKey = LogArchiveKeys.dataKey(header.firstIndex(), header.lastIndex(), term);
    final var object =
        new LogObject()
            .setKey(relativeKey)
            .setTerm(term)
            .setFirstIndex(header.firstIndex())
            .setLastIndex(header.lastIndex())
            .setFirstPosition(header.firstPosition())
            .setLastPosition(header.lastPosition())
            .setEntryCount(header.entryCount())
            .setSizeBytes(encoded.bytes().length)
            .setSha256(encoded.sha256());
    inFlight = new InFlight(prefix + relativeKey, encoded.bytes(), object);
    lastCutMillis = ActorClock.currentTimeMillis();
    state = State.UPLOADING;
    upload();
  }

  private void upload() {
    final var current = inFlight;
    if (state != State.UPLOADING || current == null) {
      return;
    }
    final long expectedEpoch = epoch;
    CompletableFuture<Void> future;
    try {
      future = store.put(current.key(), current.bytes());
    } catch (final RuntimeException e) {
      future = CompletableFuture.failedFuture(e);
    }
    future.whenComplete(
        (ignored, error) ->
            actor.run(
                () -> {
                  if (expectedEpoch != epoch || state != State.UPLOADING) {
                    return;
                  }
                  if (error != null) {
                    LOG.warn(
                        "Partition {}: failed to upload log object {}, retrying in {}",
                        partitionId,
                        current.key(),
                        backoff,
                        error);
                    retryLater(this::upload);
                  } else {
                    state = State.AWAIT_COMMIT;
                    writeArchiveLog();
                  }
                }));
  }

  private void writeArchiveLog() {
    final var current = inFlight;
    if (state != State.AWAIT_COMMIT || current == null) {
      return;
    }
    if (current.object().getFirstIndex() != archivedIndex + 1) {
      // F moved while uploading (e.g. a command of an earlier term was processed); the object no
      // longer continues F and would be rejected
      resetTo(archivedIndex);
      return;
    }
    final var record = new LogArchiveRecord().setTimelineId(timelineId).addObject(current.object());
    if (!write(LogArchiveIntent.ARCHIVE_LOG, record)) {
      retryLater(this::writeArchiveLog);
    }
  }

  private void logArchived(final long newArchivedIndex) {
    if (state == State.CLOSED || state == State.AWAIT_RECOVERY || state == State.AWAIT_TIMELINE) {
      return;
    }
    if (newArchivedIndex <= archivedIndex) {
      return;
    }
    final var current = inFlight;
    if (state == State.AWAIT_COMMIT
        && current != null
        && current.object().getLastIndex() == newArchivedIndex) {
      // our object was committed; the source is already positioned right after it
      inFlight = null;
      clearPending();
      archivedIndex = newArchivedIndex;
      backoff = INITIAL_BACKOFF;
      state = State.IDLE;
      tryCut();
    } else if (state == State.IDLE || state == State.BACKOFF) {
      resetTo(newArchivedIndex);
    } else {
      // an object of an earlier term was archived while ours is in flight; ours no longer
      // continues F and is reset either before writing or by its rejection
      archivedIndex = newArchivedIndex;
    }
  }

  private void archiveLogRejected(final long currentArchivedIndex) {
    if (state == State.AWAIT_COMMIT
        || ((state == State.IDLE || state == State.BACKOFF)
            && currentArchivedIndex != archivedIndex)) {
      LOG.debug(
          "Partition {}: ARCHIVE_LOG rejected, continuing after archived index {}",
          partitionId,
          currentArchivedIndex);
      resetTo(currentArchivedIndex);
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Helpers
  // -----------------------------------------------------------------------------------------------

  private boolean write(final LogArchiveIntent intent, final LogArchiveRecord record) {
    final var result =
        writer.tryWrite(
            WriteContext.internal(),
            LogAppendEntry.of(
                new RecordMetadata()
                    .recordType(RecordType.COMMAND)
                    .valueType(ValueType.LOG_ARCHIVE)
                    .intent(intent),
                record));
    if (result instanceof Either.Left<?, ?>(final var failure)) {
      LOG.warn(
          "Partition {}: failed to write {} ({}), retrying in {}",
          partitionId,
          intent,
          failure,
          backoff);
      return false;
    }
    return true;
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

  private void clearPending() {
    pending.clear();
    pendingBytes = 0;
  }

  /** The state of the archiver; visible for tests. */
  State state() {
    return state;
  }

  enum State {
    /** Waiting for the processor to deliver the replicated meta. */
    AWAIT_RECOVERY,
    /** START_TIMELINE written, waiting for TIMELINE_STARTED. */
    AWAIT_TIMELINE,
    IDLE,
    READING,
    UPLOADING,
    AWAIT_COMMIT,
    /** A reset from F is scheduled after a failure that was not tied to the in-flight object. */
    BACKOFF,
    CLOSED
  }

  private record InFlight(String key, byte[] bytes, LogObject object) {}
}
