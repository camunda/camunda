/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.leader;

import io.camunda.zeebe.backup.logarchive.format.EncodedSegment;
import io.camunda.zeebe.backup.logarchive.format.LogArchiveFrame;
import io.camunda.zeebe.backup.logarchive.format.SegmentIdentity;
import io.camunda.zeebe.backup.logarchive.format.SegmentV1Encoder;
import io.camunda.zeebe.backup.logarchive.leader.LogArchiverTimeline.Effective;
import io.camunda.zeebe.backup.logarchive.leader.LogArchiverTimeline.Takeover;
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
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.util.Either;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Uploads committed journal entries as log objects and commits each upload with {@code ARCHIVE_LOG}
 * (spec section 8.1). Runs only on the partition leader, one object in flight at a time: {@code
 * IDLE → READING → UPLOADING → AWAIT_COMMIT → IDLE}.
 *
 * <p>Nothing happens before {@link #onRecoveryChecked(LogArchiveMeta, boolean)}: the publisher
 * delivers the replicated meta once it has checked the bucket against it (spec section 8.4). The
 * archiver then either continues the recovered timeline from its F, or starts a timeline with
 * {@code START_TIMELINE}: the effective one (timeline file, else configuration) if the recovered
 * timeline is a different one, or a fork if the bucket is ahead of the recovered state. A timeline
 * is only started under an id that has nothing in the bucket yet, so that no history is ever
 * overwritten.
 *
 * <p>The very first timeline of a partition (no timeline in the state, no timeline file) is not
 * started on recovery, but only once {@link #activate()} is called (spec section 18.1): during a
 * rolling upgrade, brokers that are not upgraded yet cannot apply a {@code LOG_ARCHIVE} record.
 * Activation is not persisted; once {@code START_TIMELINE} is applied, the state holds a timeline
 * and later leaders resume it. A timeline file is only written by a restore, which runs on upgraded
 * brokers, so a timeline from it starts without activation.
 *
 * <p>All state lives on the actor thread. Listener callbacks and commit wake-ups arrive on other
 * threads and hop onto the actor. Every asynchronous continuation (store PUT, retry timer) captures
 * the current {@code epoch} and is dropped if the cursor was reset in the meantime, so a late
 * completion can never act on a stale range.
 */
public final class LogArchiver extends Actor
    implements LogArchiveListener,
        LogArchiveRecoveryListener,
        CommitListener,
        LogArchiveActivation {

  private static final Logger LOG = LoggerFactory.getLogger(LogArchiver.class);
  private static final Duration INITIAL_BACKOFF = Duration.ofMillis(100);
  private static final Duration MAX_BACKOFF = Duration.ofSeconds(10);
  // fixed part of a frame in the log object format: six longs and the record length
  // what the journal adds to a serialized record: frame version byte and RecordMetadata
  private static final int FRAME_OVERHEAD_BYTES = 1 + 8 + 8 + 4;
  // one or more fork suffixes as produced by forkTimelineId, e.g. ".f51" or ".f51-2.f7"
  private static final Pattern FORK_SUFFIXES = Pattern.compile("(\\.f\\d+(-\\d+)?)+");

  private final int partitionId;
  private final String clusterId;
  private final String physicalTenantId;
  private final long term;
  private final LogArchiveStore store;
  private final LogStreamWriter writer;
  private final LogArchiverEntrySource source;
  private final Supplier<Effective> timelineResolver;
  private final long cutMaxBytes;
  private final Duration cutInterval;
  private final Duration idleCutInterval;
  private final AtomicBoolean wakeUpScheduled = new AtomicBoolean();

  // all fields below are only accessed on the actor thread
  private State state = State.AWAIT_RECOVERY;
  private String timelineId = "";
  private String prefix = "";
  private long startIndex = -1;
  private String parentTimelineId = "";
  // where the timeline being started takes over its parent, -1 if unknown (spec section 19.3)
  private long parentIndex = -1;
  private long parentPosition = -1;
  // whether the replicated state holds a timeline, as far as this archiver knows
  private boolean stateHasTimeline;
  // set by activate(); lets the first timeline start once recovery finds none
  private boolean activated;
  // the effective timeline to start once activated, while in AWAIT_ACTIVATION
  private String awaitedTimelineId = "";
  // A: the last index known to be archived (F as far as this archiver knows)
  private long archivedIndex = -1;
  private long nextReadIndex = -1;
  private final List<LogArchiveFrame> pending = new ArrayList<>();
  private long pendingBytes;
  private long pendingLatestTimestamp = -1;
  // true once a pending entry carries a record of another value type than LOG_ARCHIVE
  private boolean pendingHasForeignEntry;
  private @Nullable InFlight inFlight;
  private long lastCutMillis;
  private long epoch;
  private Duration backoff = INITIAL_BACKOFF;

  /**
   * @param clusterId the cluster id written into the identity of every object
   * @param physicalTenantId the physical tenant id written into the identity of every object
   * @param term the leader's term; written into the object key and header
   * @param source committed entries; owned and closed by the archiver
   * @param timelineResolver returns the effective timeline (timeline file, else configuration) and
   *     whether it comes from the timeline file; called on the actor when recovering
   * @param cutMaxBytes cut an object once the pending entries reach this size
   * @param cutInterval cut an object at least this often while entries are pending
   * @param idleCutInterval cut an object at least this often while all pending entries are the log
   *     archive's own (see {@link LogArchiverEntry#archiveOwn()})
   */
  public LogArchiver(
      final int partitionId,
      final String clusterId,
      final String physicalTenantId,
      final long term,
      final LogArchiveStore store,
      final LogStreamWriter writer,
      final LogArchiverEntrySource source,
      final Supplier<Effective> timelineResolver,
      final long cutMaxBytes,
      final Duration cutInterval,
      final Duration idleCutInterval) {
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
    if (idleCutInterval.isNegative() || idleCutInterval.isZero()) {
      throw new IllegalArgumentException(
          "idleCutInterval must be positive, but was " + idleCutInterval);
    }
    this.partitionId = partitionId;
    this.clusterId = clusterId;
    this.physicalTenantId = physicalTenantId;
    this.term = term;
    this.store = store;
    this.writer = writer;
    this.source = source;
    this.timelineResolver = timelineResolver;
    this.cutMaxBytes = cutMaxBytes;
    this.cutInterval = cutInterval;
    this.idleCutInterval = idleCutInterval;
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
  public void onRecoveryChecked(final LogArchiveMeta meta, final boolean bucketAhead) {
    actor.run(() -> recover(meta, bucketAhead));
  }

  @Override
  public ActorFuture<Boolean> activate() {
    return actor.call(
        () -> {
          if (state == State.CLOSED) {
            return false;
          }
          if (!activated) {
            LOG.info("Partition {}: log archive activated", partitionId);
          }
          activated = true;
          if (state == State.AWAIT_ACTIVATION) {
            startFirstTimeline(awaitedTimelineId);
          }
          return stateHasTimeline;
        });
  }

  @Override
  public void onTimelineStarted(final String startedTimelineId) {
    actor.run(
        () -> {
          stateHasTimeline = true;
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

  private void recover(final LogArchiveMeta meta, final boolean bucketAhead) {
    if (state == State.CLOSED) {
      return;
    }
    epoch++;
    inFlight = null;
    clearPending();
    backoff = INITIAL_BACKOFF;
    final var effective = timelineResolver.get();
    final var effectiveTimelineId = effective.timelineId();
    final var recoveredTimelineId = meta.getTimelineId();
    stateHasTimeline = !recoveredTimelineId.isEmpty();

    if (!stateHasTimeline && !effective.fromTimelineFile()) {
      if (activated) {
        startFirstTimeline(effectiveTimelineId);
      } else {
        LOG.info(
            "Partition {}: no log archive timeline yet; waiting for activation to start '{}'",
            partitionId,
            effectiveTimelineId);
        awaitedTimelineId = effectiveTimelineId;
        state = State.AWAIT_ACTIVATION;
      }
    } else if (!continuesTimeline(recoveredTimelineId, effectiveTimelineId)) {
      LOG.info(
          "Partition {}: log archive timeline is '{}', but the effective timeline is '{}'; starting it",
          partitionId,
          recoveredTimelineId,
          effectiveTimelineId);
      startTimeline(
          attempt ->
              attempt == 0 ? effectiveTimelineId : forkTimelineId(effectiveTimelineId, 1, attempt),
          0,
          recoveredTimelineId,
          effective.takeover());
    } else if (bucketAhead) {
      LOG.warn(
          "Partition {}: the log archive bucket is ahead of the recovered state of timeline '{}'"
              + " (seq {} exists); the state was rolled back, forking a new timeline",
          partitionId,
          recoveredTimelineId,
          meta.getNextSeq());
      startTimeline(
          attempt -> forkTimelineId(recoveredTimelineId, meta.getNextSeq(), attempt),
          1,
          recoveredTimelineId,
          null);
    } else {
      timelineId = recoveredTimelineId;
      prefix = LogArchiveKeys.partitionPrefix(timelineId, partitionId);
      LOG.info(
          "Partition {}: resuming log archive timeline {} after index {}",
          partitionId,
          timelineId,
          meta.getArchivedIndex());
      resetTo(meta.getArchivedIndex());
    }
  }

  /** Starts the configured timeline as the partition's first one, from the first index on disk. */
  private void startFirstTimeline(final String effectiveTimelineId) {
    LOG.info(
        "Partition {}: log archive is activated; starting the first timeline '{}'",
        partitionId,
        effectiveTimelineId);
    startTimeline(
        attempt ->
            attempt == 0 ? effectiveTimelineId : forkTimelineId(effectiveTimelineId, 1, attempt),
        0,
        "",
        null);
  }

  /**
   * Returns the id of a timeline forked from {@code timelineId} because the bucket already holds
   * {@code commits/<seq>} of it: {@code <timelineId>.f<seq>}, and {@code <timelineId>.f<seq>-<n>}
   * for the n-th attempt if that one is taken too (the state can be rolled back to the same point
   * more than once). It is derived from the replicated state only, and it names the point of
   * divergence for operators.
   */
  static String forkTimelineId(final String timelineId, final long seq, final int attempt) {
    final var fork = timelineId + ".f" + seq;
    return attempt <= 1 ? fork : fork + "-" + attempt;
  }

  /**
   * Returns whether the recovered timeline is the effective one or a fork of it. A fork exists only
   * in the replicated state, not in the timeline file or the configuration, so it must not count as
   * a different timeline: that would start the effective one again on the next leader.
   */
  static boolean continuesTimeline(final String recoveredTimelineId, final String effective) {
    if (recoveredTimelineId.isEmpty() || !recoveredTimelineId.startsWith(effective)) {
      return false;
    }
    final var suffix = recoveredTimelineId.substring(effective.length());
    return suffix.isEmpty() || FORK_SUFFIXES.matcher(suffix).matches();
  }

  /**
   * Starts the first of the {@code candidates} (from {@code attempt} on) that has nothing in the
   * bucket yet. A non-empty prefix means that another history already lives there, e.g. because the
   * state was rolled back behind the point where that timeline was started; writing into it would
   * overwrite that history.
   */
  private void startTimeline(
      final IntFunction<String> candidates,
      final int attempt,
      final String parent,
      final @Nullable Takeover takeover) {
    startIndex = source.firstIndex();
    parentTimelineId = parent;
    if (takeover != null && takeover.parentTimelineId().equals(parent)) {
      parentIndex = takeover.parentIndex();
      parentPosition = takeover.parentPosition();
    } else {
      parentIndex = -1;
      parentPosition = -1;
    }
    timelineId = "";
    state = State.RESOLVE_TIMELINE;
    resolveTimeline(candidates, attempt);
  }

  private void resolveTimeline(final IntFunction<String> candidates, final int attempt) {
    if (state != State.RESOLVE_TIMELINE) {
      return;
    }
    final var candidate = candidates.apply(attempt);
    final var candidatePrefix = LogArchiveKeys.partitionPrefix(candidate, partitionId);
    final long expectedEpoch = epoch;
    CompletableFuture<List<String>> future;
    try {
      future = store.list(candidatePrefix);
    } catch (final RuntimeException e) {
      future = CompletableFuture.failedFuture(e);
    }
    future.whenComplete(
        (keys, error) ->
            actor.run(
                () -> {
                  if (expectedEpoch != epoch || state != State.RESOLVE_TIMELINE) {
                    return;
                  }
                  if (error != null) {
                    LOG.warn(
                        "Partition {}: failed to list {}, retrying in {}",
                        partitionId,
                        candidatePrefix,
                        backoff,
                        error);
                    retryLater(() -> resolveTimeline(candidates, attempt));
                  } else if (!keys.isEmpty()) {
                    LOG.warn(
                        "Partition {}: log archive timeline '{}' already exists in the bucket;"
                            + " not writing into it",
                        partitionId,
                        candidate);
                    resolveTimeline(candidates, attempt + 1);
                  } else {
                    timelineId = candidate;
                    prefix = candidatePrefix;
                    backoff = INITIAL_BACKOFF;
                    state = State.AWAIT_TIMELINE;
                    LOG.info(
                        "Partition {}: starting log archive timeline '{}' (parent '{}') at index {}",
                        partitionId,
                        timelineId,
                        parentTimelineId,
                        startIndex);
                    writeStartTimeline();
                  }
                }));
  }

  private void writeStartTimeline() {
    if (state != State.AWAIT_TIMELINE) {
      return;
    }
    final var record =
        new LogArchiveRecord()
            .setTimelineId(timelineId)
            .setStartIndex(startIndex)
            .setParentTimelineId(parentTimelineId)
            .setParentIndex(parentIndex)
            .setParentPosition(parentPosition)
            .setStartedAt(ActorClock.currentTimeMillis());
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
      final var entry = source.next();
      final var frame = entry.frame();
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
      pendingHasForeignEntry |= !entry.archiveOwn();
      pendingLatestTimestamp = Math.max(pendingLatestTimestamp, entry.latestTimestamp());
      nextReadIndex++;
    }

    // Every cut is followed by ARCHIVE_LOG and LOG_ARCHIVED (and, now and then, other LOG_ARCHIVE
    // records), which are committed entries themselves. Cutting them on cutInterval would make an
    // idle partition archive its own bookkeeping forever, so entries that hold nothing else wait
    // for idleCutInterval. They are still archived, in order, with the next cut.
    final boolean sizeReached = pendingBytes >= cutMaxBytes;
    final Duration interval = pendingHasForeignEntry ? cutInterval : idleCutInterval;
    final boolean intervalElapsed =
        ActorClock.currentTimeMillis() - lastCutMillis >= interval.toMillis();
    if (pending.isEmpty() || !(sizeReached || intervalElapsed)) {
      state = State.IDLE;
      return;
    }
    cut();
  }

  private void cut() {
    final EncodedSegment encoded;
    try {
      encoded =
          SegmentV1Encoder.encode(
              new SegmentIdentity(clusterId, physicalTenantId, partitionId),
              timelineId,
              pending,
              pendingLatestTimestamp);
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
    final var footer = encoded.footer();
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
            .setSizeBytes(encoded.bytes().length)
            .setSha256(encoded.sha256())
            .setBodyLength(footer.bodyLength())
            .setLastRecordTimestamp(footer.latestTimestamp());
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
    if (state == State.CLOSED
        || state == State.AWAIT_RECOVERY
        || state == State.AWAIT_ACTIVATION
        || state == State.RESOLVE_TIMELINE
        || state == State.AWAIT_TIMELINE) {
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
    pendingLatestTimestamp = -1;
    pendingHasForeignEntry = false;
  }

  /** The state of the archiver; visible for tests. */
  State state() {
    return state;
  }

  enum State {
    /** Waiting for the processor to deliver the replicated meta. */
    AWAIT_RECOVERY,
    /**
     * The state holds no timeline and there is no timeline file: waiting for {@link #activate()}
     * before starting the configured timeline. Nothing is written in this state.
     */
    AWAIT_ACTIVATION,
    /** A timeline must be started; looking for an id that has nothing in the bucket yet. */
    RESOLVE_TIMELINE,
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
