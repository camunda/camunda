/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.processing;

import io.camunda.zeebe.backup.logarchive.model.CommitEntry;
import io.camunda.zeebe.backup.logarchive.model.HandoverJson;
import io.camunda.zeebe.backup.logarchive.model.LogArchiveJson;
import io.camunda.zeebe.backup.logarchive.model.LogArchiveKeys;
import io.camunda.zeebe.backup.logarchive.model.TimelineFile;
import io.camunda.zeebe.backup.logarchive.state.DbLogArchiveState;
import io.camunda.zeebe.backup.logarchive.state.LogArchiveMeta;
import io.camunda.zeebe.backup.processing.state.CheckpointState;
import io.camunda.zeebe.backup.processing.state.DbCheckpointState;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.management.ArchivedSnapshot;
import io.camunda.zeebe.protocol.impl.record.value.management.LogArchiveRecord;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.management.LogArchiveIntent;
import io.camunda.zeebe.protocol.record.value.management.LogArchiveRecordValue.LogObjectValue;
import io.camunda.zeebe.stream.api.ProcessingResult;
import io.camunda.zeebe.stream.api.ProcessingResultBuilder;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.RecordProcessor;
import io.camunda.zeebe.stream.api.RecordProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Processes and replays {@link ValueType#LOG_ARCHIVE} records (spec section 6).
 *
 * <p>Every valid command is turned into an event that is applied through the {@link
 * LogArchiveEventApplier}, both while processing and while replaying. An invalid command is
 * rejected without any state change. While processing, a post commit task notifies the {@link
 * LogArchiveListener} and hands requests to the {@link LogArchivePublication}; neither happens
 * while replaying.
 */
public final class LogArchiveRecordsProcessor
    implements RecordProcessor, StreamProcessorLifecycleAware {

  /**
   * The timestamp written into commit entries. The timestamp of the event record is assigned when
   * the batch is written, so it is unknown while processing and cannot be the same on the leader
   * and on followers. A constant keeps the commit entry a function of the event record (I4).
   */
  public static final long NO_RECORD_TIMESTAMP = -1L;

  /**
   * The handover position recorded by a partition that took no part in the handover (spec section
   * 18.4): the position before its first record, as everything after it is in the log archive.
   */
  public static final long HANDOVER_POSITION_FROM_START = 0L;

  /** The position of the first record of every partition's log. */
  private static final long FIRST_LOG_POSITION = 1L;

  private static final Logger LOG = LoggerFactory.getLogger(LogArchiveRecordsProcessor.class);

  private final LogArchiveProcessorConfig config;
  private volatile @Nullable LogArchiveListener listener;
  private volatile @Nullable LogArchivePublication publication;

  @SuppressWarnings("NullAway.Init")
  private DbLogArchiveState state;

  @SuppressWarnings("NullAway.Init")
  private CheckpointState checkpointState;

  @SuppressWarnings("NullAway.Init")
  private LogArchiveEventApplier applier;

  public LogArchiveRecordsProcessor(final LogArchiveProcessorConfig config) {
    this.config = config;
  }

  /** Registers the receiver of in-memory notifications. Replaces a previous one. */
  public void setListener(final @Nullable LogArchiveListener listener) {
    this.listener = listener;
  }

  /** Registers the receiver of bucket writes. Replaces a previous one. */
  public void setPublication(final @Nullable LogArchivePublication publication) {
    this.publication = publication;
  }

  /**
   * The position written into the commit entry of an event: the position of the command it resulted
   * from. The position of the event itself is unknown while processing. Given the event when
   * replaying and the command when processing, both yield the same value.
   */
  static long recordPositionOf(final TypedRecord<?> eventOrCommand, final boolean isEvent) {
    return isEvent ? eventOrCommand.getSourceRecordPosition() : eventOrCommand.getPosition();
  }

  @Override
  public void init(final RecordProcessorContext context) {
    state = new DbLogArchiveState(context.getZeebeDb(), context.getTransactionContext());
    checkpointState = new DbCheckpointState(context.getZeebeDb(), context.getTransactionContext());
    applier = new LogArchiveEventApplier(config.partitionId(), state);
    context.addLifecycleListeners(List.of(this));
  }

  @Override
  public boolean accepts(final ValueType valueType) {
    return valueType == ValueType.LOG_ARCHIVE;
  }

  @Override
  public void replay(final TypedRecord record) {
    if (record.getValueType() != ValueType.LOG_ARCHIVE) {
      throw new IllegalArgumentException("Unknown record");
    }
    if (!record.getIntent().isEvent()) {
      throw new IllegalArgumentException(
          "Expected to replay event but got non-event with intent: %s"
              .formatted(record.getIntent()));
    }
    final var intent = (LogArchiveIntent) record.getIntent();
    applier.apply(
        intent,
        (LogArchiveRecord) record.getValue(),
        recordPositionOf(record, true),
        NO_RECORD_TIMESTAMP);
  }

  @Override
  public ProcessingResult process(
      final TypedRecord record, final ProcessingResultBuilder resultBuilder) {
    if (record.getValueType() != ValueType.LOG_ARCHIVE
        || !(record.getIntent() instanceof final LogArchiveIntent intent)) {
      throw new IllegalArgumentException(
          "Unknown value type %s for record %s".formatted(record.getValueType(), record));
    }
    final var command = (LogArchiveRecord) record.getValue();
    final var meta = state.meta();

    final var rejection =
        switch (intent) {
          case START_TIMELINE -> validateStartTimeline(command, meta);
          case ARCHIVE_LOG -> validateArchiveLog(command, meta);
          case ARCHIVE_SNAPSHOT -> validateArchiveSnapshot(command, meta);
          case SEAL_INDEX -> validateSealIndex(command, meta);
          case CONFIRM_PUBLISHED -> validateConfirmPublished(command, meta);
          case COMPLETE_HANDOVER -> validateCompleteHandover(command, meta);
          case MERGE_LOG -> validateMergeLog(command, meta);
          case RELEASE_LOG -> validateReleaseLog(command, meta);
          default ->
              throw new IllegalArgumentException(
                  "Unknown log archive intent %s".formatted(record.getIntent()));
        };

    if (rejection != null) {
      reject(record, intent, command, meta, rejection, resultBuilder);
    } else {
      accept(record, intent, command, meta, resultBuilder);
    }
    return resultBuilder.build();
  }

  @Override
  public ProcessingResult onProcessingError(
      final Throwable processingException,
      final TypedRecord record,
      final ProcessingResultBuilder processingResultBuilder) {
    LOG.error("Could not process log archive record {}.", record.getValue(), processingException);
    // Continuing after a failed command could skip an event that followers or a later leader
    // expect, so the only safe choice is to stop the stream processor.
    throw new RuntimeException(processingException);
  }

  /**
   * Hands the leader components everything that may not have reached the bucket yet: every outbox
   * row in order, then a root for the latest seq and a seal request if the open chunk is full.
   * Writing the same key again is harmless (I4). The publisher holds these requests until it has
   * checked that the bucket is not ahead of this state (rolled back state); if it is, it drops them
   * and the archiver forks a new timeline (spec section 8.4).
   */
  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    final var meta = state.meta();
    final var currentListener = listener;
    if (currentListener != null) {
      currentListener.onRecovered(meta);
      final var newest =
          state.snapshots().stream()
              .max(Comparator.comparingLong(ArchivedSnapshot::getProcessedPosition));
      currentListener.onNewestArchivedSnapshotRecovered(
          newest.map(ArchivedSnapshot::getId).orElse(""),
          newest.map(ArchivedSnapshot::getProcessedPosition).orElse(-1L));
    }
    final var currentPublication = publication;
    if (currentPublication == null || meta.getTimelineId().isEmpty()) {
      return;
    }

    final var timelineId = meta.getTimelineId();
    final var outbox = state.outboxAfter(meta.getLastPublishedSeq());
    for (final var row : outbox) {
      final var entry = LogArchiveJson.read(row.commitEntryJson(), CommitEntry.class);
      currentPublication.publish(
          new PublishRequest(
              timelineId,
              row.seq(),
              row.commitEntryJson(),
              null,
              null,
              handoverOf(meta, entry),
              timelineOf(entry)));
    }

    final long rootSeq = meta.getNextSeq() - 1;
    final byte[] rootJson = outbox.isEmpty() ? null : rootJson(meta, rootSeq);
    final var seal = pendingSeal(meta);
    if (rootJson != null || seal != null) {
      currentPublication.publish(
          new PublishRequest(timelineId, rootJson != null ? rootSeq : -1, null, rootJson, seal));
    }
  }

  /**
   * The handover of an outbox row if it is the handover commit entry, which the publisher writes to
   * the old backups. Only the one row that follows the recorded handover can be that entry, so
   * nothing is parsed unless a handover is recorded.
   */
  private static @Nullable HandoverJson handoverOf(
      final LogArchiveMeta meta, final CommitEntry entry) {
    if (!meta.hasHandover()) {
      return null;
    }
    return entry.type() == CommitEntry.Type.HANDOVER ? entry.handover() : null;
  }

  /** The registry file of an outbox row if it is the {@code TIMELINE_STARTED} commit entry. */
  private static @Nullable TimelineFile timelineOf(final CommitEntry entry) {
    return entry.type() == CommitEntry.Type.TIMELINE_STARTED
        ? LogArchiveJsonMapper.timelineFile(entry)
        : null;
  }

  // ---------------------------------------------------------------------------------------------
  // Accepting commands
  // ---------------------------------------------------------------------------------------------

  private void accept(
      final TypedRecord<?> command,
      final LogArchiveIntent intent,
      final LogArchiveRecord commandValue,
      final LogArchiveMeta meta,
      final ProcessingResultBuilder resultBuilder) {
    final var eventIntent = eventIntentOf(intent);
    // a handover is requested without knowing the timeline: it is always recorded on the current
    // one
    final var event =
        new LogArchiveRecord()
            .setTimelineId(
                intent == LogArchiveIntent.COMPLETE_HANDOVER
                    ? meta.getTimelineId()
                    : commandValue.getTimelineId());

    switch (intent) {
      case START_TIMELINE ->
          event
              .setSeq(1)
              .setStartIndex(commandValue.getStartIndex())
              .setParentTimelineId(commandValue.getParentTimelineId())
              .setParentIndex(commandValue.getParentIndex())
              .setParentPosition(commandValue.getParentPosition())
              .setStartedAt(commandValue.getStartedAt());
      case ARCHIVE_LOG -> {
        event.setSeq(meta.getNextSeq());
        commandValue.getObjects().forEach(event::addObject);
      }
      case ARCHIVE_SNAPSHOT ->
          event.setSeq(meta.getNextSeq()).setSnapshot(commandValue.getSnapshot());
      case SEAL_INDEX -> event.setSeq(meta.getNextSeq()).setChunk(commandValue.getChunk());
      case CONFIRM_PUBLISHED -> event.setPublishedSeq(commandValue.getPublishedSeq());
      case MERGE_LOG -> {
        event.setSeq(meta.getNextSeq());
        commandValue.getObjects().forEach(event::addObject);
      }
      case RELEASE_LOG ->
          event.setSeq(meta.getNextSeq()).setReleasedIndex(commandValue.getReleasedIndex());
      case COMPLETE_HANDOVER ->
          // the position is that of the confirmed handover backup, validated to match the command;
          // a partition without one took no part in the handover and records it from its start
          event
              .setSeq(meta.getNextSeq())
              .setHandoverCheckpointId(commandValue.getHandoverCheckpointId())
              .setHandoverPosition(
                  checkpointState.getLatestHandoverBackupId() == CheckpointState.NO_CHECKPOINT
                      ? HANDOVER_POSITION_FROM_START
                      : checkpointState.getLatestHandoverBackupPosition());
      default -> throw new IllegalStateException("Unexpected intent " + intent);
    }

    final long openObjectsBefore = state.openObjectCount();

    // applying completes the event with F and the covered checkpoints, so it must happen before
    // the event is appended
    final var commitEntryJson =
        applier.apply(eventIntent, event, recordPositionOf(command, false), NO_RECORD_TIMESTAMP);

    resultBuilder.appendRecord(
        command.getKey(),
        event,
        new RecordMetadata()
            .recordType(RecordType.EVENT)
            .intent(eventIntent)
            .rejectionType(RejectionType.NULL_VAL)
            .rejectionReason(""));

    final var newMeta = state.meta();
    final var request =
        publishRequestFor(eventIntent, event, newMeta, commitEntryJson, openObjectsBefore);
    final String snapshotId =
        eventIntent == LogArchiveIntent.SNAPSHOT_ARCHIVED ? event.getSnapshot().getId() : "";
    final long chunkNo =
        eventIntent == LogArchiveIntent.INDEX_SEALED ? event.getChunk().getChunkNo() : -1;
    final long publishedSeq = event.getPublishedSeq();
    resultBuilder.appendPostCommitTask(
        () -> {
          if (request != null) {
            final var currentPublication = publication;
            if (currentPublication != null) {
              currentPublication.publish(request);
            }
          }
          final var currentListener = listener;
          if (currentListener == null) {
            return;
          }
          switch (eventIntent) {
            case TIMELINE_STARTED -> currentListener.onTimelineStarted(newMeta.getTimelineId());
            case LOG_ARCHIVED ->
                currentListener.onLogArchived(
                    newMeta.getArchivedIndex(), newMeta.getArchivedPosition());
            case SNAPSHOT_ARCHIVED -> currentListener.onSnapshotArchived(snapshotId);
            case INDEX_SEALED -> currentListener.onIndexSealed(chunkNo);
            case PUBLISHED -> currentListener.onPublished(publishedSeq);
            case LOG_MERGED ->
                currentListener.onLogMerged(newMeta.getMergedIndex(), newMeta.getMergedPosition());
            case LOG_RELEASED -> currentListener.onLogReleased(newMeta.getReleasedIndex());
            default -> {}
          }
        });
  }

  private @Nullable PublishRequest publishRequestFor(
      final LogArchiveIntent eventIntent,
      final LogArchiveRecord event,
      final LogArchiveMeta meta,
      final byte @Nullable [] commitEntryJson,
      final long openObjectsBefore) {
    if (commitEntryJson == null) {
      return null;
    }
    final long seq = event.getSeq();
    final boolean sealed = eventIntent == LogArchiveIntent.INDEX_SEALED;
    // the handover always gets a root: it carries the handover and confirms the publication, which
    // removes the outbox row that drives the write of the old-side handover file
    final boolean handover = eventIntent == LogArchiveIntent.HANDOVER_COMPLETED;
    final byte[] rootJson =
        sealed || handover || seq % config.rootEvery() == 0 ? rootJson(meta, seq) : null;
    // A full chunk is requested once when it becomes due; the publisher keeps it pending until the
    // SEAL_INDEX is processed. Asking on every LOG_ARCHIVED would repeat the same seal.
    final boolean crossedThreshold =
        eventIntent == LogArchiveIntent.LOG_ARCHIVED
            && openObjectsBefore < config.chunkSize()
            && state.openObjectCount() >= config.chunkSize();
    final var seal = sealed || crossedThreshold ? pendingSeal(meta) : null;
    return new PublishRequest(
        event.getTimelineId(),
        seq,
        commitEntryJson,
        rootJson,
        seal,
        handover
            ? new HandoverJson(event.getHandoverCheckpointId(), event.getHandoverPosition())
            : null,
        eventIntent == LogArchiveIntent.TIMELINE_STARTED
            ? LogArchiveJsonMapper.timelineFile(config.partitionId(), event)
            : null);
  }

  private byte[] rootJson(final LogArchiveMeta meta, final long seq) {
    return LogArchiveJson.write(
        LogArchiveJsonMapper.rootFile(
            config.partitionId(),
            meta,
            seq,
            state.chunks(),
            state.openObjects(),
            state.mergedObjects(),
            state.snapshots()));
  }

  /** The seal request for the next chunk if the open chunk is full. */
  private @Nullable SealRequest pendingSeal(final LogArchiveMeta meta) {
    if (state.openObjectCount() < config.chunkSize()) {
      return null;
    }
    return LogArchiveJsonMapper.sealRequest(
        config.partitionId(),
        meta.getTimelineId(),
        meta.getNextChunkNo(),
        state.firstOpenObjects(config.chunkSize()));
  }

  private static LogArchiveIntent eventIntentOf(final LogArchiveIntent command) {
    return switch (command) {
      case START_TIMELINE -> LogArchiveIntent.TIMELINE_STARTED;
      case ARCHIVE_LOG -> LogArchiveIntent.LOG_ARCHIVED;
      case ARCHIVE_SNAPSHOT -> LogArchiveIntent.SNAPSHOT_ARCHIVED;
      case SEAL_INDEX -> LogArchiveIntent.INDEX_SEALED;
      case CONFIRM_PUBLISHED -> LogArchiveIntent.PUBLISHED;
      case COMPLETE_HANDOVER -> LogArchiveIntent.HANDOVER_COMPLETED;
      case MERGE_LOG -> LogArchiveIntent.LOG_MERGED;
      case RELEASE_LOG -> LogArchiveIntent.LOG_RELEASED;
      default -> throw new IllegalArgumentException("Not a command: " + command);
    };
  }

  // ---------------------------------------------------------------------------------------------
  // Rejecting commands
  // ---------------------------------------------------------------------------------------------

  private record Rejection(RejectionType type, String reason) {}

  private void reject(
      final TypedRecord<?> command,
      final LogArchiveIntent intent,
      final LogArchiveRecord commandValue,
      final LogArchiveMeta meta,
      final Rejection rejection,
      final ProcessingResultBuilder resultBuilder) {
    resultBuilder.appendRecord(
        command.getKey(),
        commandValue,
        new RecordMetadata()
            .recordType(RecordType.COMMAND_REJECTION)
            .intent(intent)
            .rejectionType(rejection.type())
            .rejectionReason(rejection.reason()));

    final long archivedIndex = meta.getArchivedIndex();
    final long mergedIndex = meta.getMergedIndex();
    final long releasedIndex = meta.getReleasedIndex();
    final var snapshotId = commandValue.getSnapshot().getId();
    // a lost or invalid seal is retried once per rejection while the chunk is still full
    final var retrySeal =
        intent == LogArchiveIntent.SEAL_INDEX && !meta.getTimelineId().isEmpty()
            ? pendingSeal(meta)
            : null;
    final var timelineId = meta.getTimelineId();
    resultBuilder.appendPostCommitTask(
        () -> {
          final var currentPublication = publication;
          if (retrySeal != null && currentPublication != null) {
            currentPublication.publish(new PublishRequest(timelineId, -1, null, null, retrySeal));
          }
          final var currentListener = listener;
          if (currentListener == null) {
            return;
          }
          switch (intent) {
            case ARCHIVE_LOG -> currentListener.onArchiveLogRejected(archivedIndex);
            case ARCHIVE_SNAPSHOT -> currentListener.onSnapshotRejected(snapshotId);
            case MERGE_LOG -> currentListener.onMergeLogRejected(mergedIndex);
            case RELEASE_LOG -> currentListener.onReleaseLogRejected(releasedIndex);
            default ->
                currentListener.onCommandRejected(intent, rejection.type(), rejection.reason());
          }
        });
  }

  private static Rejection invalidState(final String reason) {
    return new Rejection(RejectionType.INVALID_STATE, reason);
  }

  private static Rejection invalidArgument(final String reason) {
    return new Rejection(RejectionType.INVALID_ARGUMENT, reason);
  }

  /** Common to all commands except START_TIMELINE. */
  private static @Nullable Rejection validateTimeline(
      final LogArchiveRecord command, final LogArchiveMeta meta) {
    if (meta.getTimelineId().isEmpty()) {
      return invalidState("Expected to process the command, but no timeline was started yet");
    }
    if (!command.getTimelineId().equals(meta.getTimelineId())) {
      return invalidState(
          "Expected the command for timeline '%s', but it is for timeline '%s'"
              .formatted(meta.getTimelineId(), command.getTimelineId()));
    }
    return null;
  }

  private static @Nullable Rejection validateStartTimeline(
      final LogArchiveRecord command, final LogArchiveMeta meta) {
    if (command.getTimelineId().isEmpty()) {
      return invalidArgument("Expected a non-empty timeline id");
    }
    if (command.getStartIndex() <= 0) {
      return invalidArgument(
          "Expected a start index greater than 0, but got " + command.getStartIndex());
    }
    if (command.getTimelineId().equals(meta.getTimelineId())) {
      return invalidState(
          "Expected a timeline different from the current one, but '%s' is already the current timeline"
              .formatted(meta.getTimelineId()));
    }
    return null;
  }

  private static @Nullable Rejection validateArchiveLog(
      final LogArchiveRecord command, final LogArchiveMeta meta) {
    final var timelineRejection = validateTimeline(command, meta);
    if (timelineRejection != null) {
      return timelineRejection;
    }
    final var objects = command.getObjects();
    if (objects.isEmpty()) {
      return invalidArgument("Expected at least one log object");
    }
    for (final LogObjectValue object : objects) {
      if (object.getFirstIndex() > object.getLastIndex()
          || object.getEntryCount() != object.getLastIndex() - object.getFirstIndex() + 1) {
        return invalidArgument(
            "Expected log object '%s' to have firstIndex <= lastIndex and an entryCount equal to the number of indexes, but got [%d, %d] with entryCount %d"
                .formatted(
                    object.getKey(),
                    object.getFirstIndex(),
                    object.getLastIndex(),
                    object.getEntryCount()));
      }
    }
    for (final LogObjectValue object : objects) {
      final var expectedKey =
          LogArchiveKeys.dataKey(object.getFirstIndex(), object.getLastIndex(), object.getTerm());
      if (!expectedKey.equals(object.getKey())
          || object.getSha256().length() != 64
          || object.getSizeBytes() <= 0) {
        return invalidArgument(
            "Expected log object '%s' to have the key '%s', a sha256 and a positive size"
                .formatted(object.getKey(), expectedKey));
      }
    }
    for (int i = 0; i + 1 < objects.size(); i++) {
      if (objects.get(i + 1).getFirstIndex() != objects.get(i).getLastIndex() + 1) {
        return invalidArgument(
            "Expected the log objects to be contiguous, but object %d ends at index %d and the next starts at %d"
                .formatted(i, objects.get(i).getLastIndex(), objects.get(i + 1).getFirstIndex()));
      }
    }
    if (objects.getFirst().getFirstIndex() != meta.getArchivedIndex() + 1) {
      return invalidState(
          "Expected the log objects to continue at index %d, but they start at %d"
              .formatted(meta.getArchivedIndex() + 1, objects.getFirst().getFirstIndex()));
    }
    return null;
  }

  /**
   * Spec section 20.1. A merge is a proposal: it must continue at M + 1 and end at the boundary of
   * an archived object, and describe the run it replaces.
   */
  private @Nullable Rejection validateMergeLog(
      final LogArchiveRecord command, final LogArchiveMeta meta) {
    final var timelineRejection = validateTimeline(command, meta);
    if (timelineRejection != null) {
      return timelineRejection;
    }
    final var objects = command.getObjects();
    if (objects.size() != 1) {
      return invalidArgument("Expected exactly one merged log object, but got " + objects.size());
    }
    final var object = objects.getFirst();
    if (object.getFirstIndex() > object.getLastIndex()
        || object.getEntryCount() != object.getLastIndex() - object.getFirstIndex() + 1) {
      return invalidArgument(
          "Expected merged log object '%s' to have firstIndex <= lastIndex and an entryCount equal to the number of indexes, but got [%d, %d] with entryCount %d"
              .formatted(
                  object.getKey(),
                  object.getFirstIndex(),
                  object.getLastIndex(),
                  object.getEntryCount()));
    }
    if (object.getFirstIndex() != meta.getMergedIndex() + 1) {
      return invalidState(
          "Expected the merged log object to continue at index %d, but it starts at %d"
              .formatted(meta.getMergedIndex() + 1, object.getFirstIndex()));
    }
    if (object.getLastIndex() > meta.getArchivedIndex()) {
      return invalidState(
          "Expected the merged log object to end at or below the archived index %d, but it ends at %d"
              .formatted(meta.getArchivedIndex(), object.getLastIndex()));
    }
    long lastPosition = -1;
    boolean endsAtBoundary = false;
    for (final var row : state.unmergedObjects()) {
      if (row.firstIndex() > object.getLastIndex()) {
        break;
      }
      lastPosition = Math.max(lastPosition, row.lastPosition());
      endsAtBoundary = row.lastIndex() == object.getLastIndex();
    }
    if (!endsAtBoundary) {
      return invalidState(
          "Expected the merged log object to end at the last index of an archived object, but %d is not one"
              .formatted(object.getLastIndex()));
    }
    if (object.getLastPosition() != lastPosition) {
      return invalidState(
          "Expected the merged log object to end at position %d, the highest of the objects it replaces, but got %d"
              .formatted(lastPosition, object.getLastPosition()));
    }
    return null;
  }

  /** Spec section 21.2: only a merged object's end can be released, and a snapshot must remain. */
  private @Nullable Rejection validateReleaseLog(
      final LogArchiveRecord command, final LogArchiveMeta meta) {
    final var timelineRejection = validateTimeline(command, meta);
    if (timelineRejection != null) {
      return timelineRejection;
    }
    final long releasedIndex = command.getReleasedIndex();
    if (releasedIndex <= meta.getReleasedIndex()) {
      return invalidState(
          "Expected a released index greater than %d, but got %d"
              .formatted(meta.getReleasedIndex(), releasedIndex));
    }
    if (state.mergedObjects().stream()
        .noneMatch(object -> object.getLastIndex() == releasedIndex)) {
      return invalidState(
          "Expected the released index to be the last index of a merged object, but %d is not one"
              .formatted(releasedIndex));
    }
    if (state.snapshots().stream().noneMatch(snapshot -> snapshot.getIndex() > releasedIndex)) {
      return invalidState(
          "Expected an archived snapshot above the released index %d, but there is none"
              .formatted(releasedIndex));
    }
    return null;
  }

  private @Nullable Rejection validateArchiveSnapshot(
      final LogArchiveRecord command, final LogArchiveMeta meta) {
    final var timelineRejection = validateTimeline(command, meta);
    if (timelineRejection != null) {
      return timelineRejection;
    }
    final var snapshot = command.getSnapshot();
    if (snapshot.getId().isEmpty()) {
      return invalidArgument("Expected a non-empty snapshot id");
    }
    if (snapshot.getIndex() > meta.getArchivedIndex()) {
      return invalidState(
          "Expected a snapshot at or below the archived index %d, but its index is %d"
              .formatted(meta.getArchivedIndex(), snapshot.getIndex()));
    }
    if (snapshot.getIndex() < meta.getStartIndex() - 1) {
      return invalidState(
          "Expected a snapshot at or above index %d, the one before the timeline starts, but its index is %d"
              .formatted(meta.getStartIndex() - 1, snapshot.getIndex()));
    }
    if (state.containsSnapshot(snapshot.getId())) {
      return invalidState(
          "Expected a new snapshot, but '%s' is already archived".formatted(snapshot.getId()));
    }
    return null;
  }

  private @Nullable Rejection validateSealIndex(
      final LogArchiveRecord command, final LogArchiveMeta meta) {
    final var timelineRejection = validateTimeline(command, meta);
    if (timelineRejection != null) {
      return timelineRejection;
    }
    final var chunk = command.getChunk();
    if (chunk.getObjectCount() <= 0 || chunk.getObjectCount() > Integer.MAX_VALUE) {
      return invalidArgument(
          "Expected a chunk with a positive object count, but got " + chunk.getObjectCount());
    }
    if (chunk.getChunkNo() != meta.getNextChunkNo()) {
      return invalidState(
          "Expected chunk number %d, but got %d"
              .formatted(meta.getNextChunkNo(), chunk.getChunkNo()));
    }
    final var objects = state.firstOpenObjects((int) chunk.getObjectCount());
    if (objects.size() < chunk.getObjectCount()) {
      return invalidState(
          "Expected at least %d open objects to seal, but there are only %d"
              .formatted(chunk.getObjectCount(), objects.size()));
    }
    // Everything but the sha256 follows from the objects; the sha256 is the publisher's to
    // vouch for.
    final var expected =
        LogArchiveJsonMapper.sealRequest(
                config.partitionId(), meta.getTimelineId(), chunk.getChunkNo(), objects)
            .chunk();
    final boolean matches =
        chunk.getFirstIndex() == expected.getFirstIndex()
            && chunk.getLastIndex() == expected.getLastIndex()
            && chunk.getFirstPosition() == expected.getFirstPosition()
            && chunk.getLastPosition() == expected.getLastPosition()
            && chunk.getFirstCheckpointId() == expected.getFirstCheckpointId()
            && chunk.getLastCheckpointId() == expected.getLastCheckpointId()
            && Objects.equals(
                chunk.getKey(),
                LogArchiveKeys.indexKey(chunk.getFirstIndex(), chunk.getLastIndex()));
    if (!matches) {
      return invalidState(
          "Expected the chunk to describe the first %d open objects (indexes [%d, %d]), but got indexes [%d, %d]"
              .formatted(
                  chunk.getObjectCount(),
                  expected.getFirstIndex(),
                  expected.getLastIndex(),
                  chunk.getFirstIndex(),
                  chunk.getLastIndex()));
    }
    return null;
  }

  private static @Nullable Rejection validateConfirmPublished(
      final LogArchiveRecord command, final LogArchiveMeta meta) {
    final var timelineRejection = validateTimeline(command, meta);
    if (timelineRejection != null) {
      return timelineRejection;
    }
    final long publishedSeq = command.getPublishedSeq();
    if (publishedSeq <= meta.getLastPublishedSeq() || publishedSeq >= meta.getNextSeq()) {
      return invalidState(
          "Expected a published seq greater than %d and less than %d, but got %d"
              .formatted(meta.getLastPublishedSeq(), meta.getNextSeq(), publishedSeq));
    }
    return null;
  }

  /**
   * Spec sections 18.2 and 18.4. The timeline id of the command is not checked: the request for a
   * handover comes from outside the log archive and cannot know it.
   *
   * <p>A partition that took part in the handover records H once H is its confirmed HANDOVER backup
   * and the archive has reached it. A partition that took no part in it, because it was added by a
   * scale-up after H, has no HANDOVER backup at all; it records H if its archive holds its whole
   * history ({@link #coversWholeHistory}), at {@link #HANDOVER_POSITION_FROM_START}.
   */
  private @Nullable Rejection validateCompleteHandover(
      final LogArchiveRecord command, final LogArchiveMeta meta) {
    if (meta.getTimelineId().isEmpty()) {
      return invalidState("Expected to complete the handover, but no timeline was started yet");
    }
    final long checkpointId = command.getHandoverCheckpointId();
    if (checkpointId <= 0) {
      return invalidArgument(
          "Expected a handover checkpoint id greater than 0, but got " + checkpointId);
    }
    if (meta.hasHandover()) {
      return invalidState(
          "Expected no handover, but the handover at checkpoint %d is already recorded"
              .formatted(meta.getHandoverCheckpointId()));
    }
    final long latestHandoverBackup = checkpointState.getLatestHandoverBackupId();
    if (latestHandoverBackup == CheckpointState.NO_CHECKPOINT) {
      return validateHandoverFromStart(checkpointId, meta);
    }
    if (latestHandoverBackup != checkpointId) {
      return invalidState(
          "Expected checkpoint %d to be a confirmed backup of type HANDOVER, but the latest confirmed one is %d"
              .formatted(checkpointId, latestHandoverBackup));
    }
    final long handoverPosition = checkpointState.getLatestHandoverBackupPosition();
    if (meta.getArchivedPosition() < handoverPosition) {
      return invalidState(
          "Expected the archived position to have reached the handover position %d, but it is %d"
              .formatted(handoverPosition, meta.getArchivedPosition()));
    }
    return null;
  }

  private @Nullable Rejection validateHandoverFromStart(
      final long checkpointId, final LogArchiveMeta meta) {
    if (!coversWholeHistory(meta)) {
      return invalidState(
          ("Expected checkpoint %d to be a confirmed backup of type HANDOVER, or the log archive to"
                  + " hold the partition's whole history (log from position %d and an archived"
                  + " snapshot), but there is no confirmed HANDOVER backup and the log archive"
                  + " starts at position %d with %d archived snapshots")
              .formatted(
                  checkpointId,
                  FIRST_LOG_POSITION,
                  meta.getFirstArchivedPosition(),
                  state.snapshots().size()));
    }
    return null;
  }

  /**
   * Whether the timeline alone restores every point of the partition's history, so no old backup is
   * needed for any of it: it holds the log from the partition's first record on, and a snapshot is
   * archived. A partition added by a scale-up does not start from an empty state but from a copy of
   * the global state of partition 1, which is not in its log; the archived snapshot is the only way
   * to bring it back. Every later restore point lies after that snapshot.
   */
  private boolean coversWholeHistory(final LogArchiveMeta meta) {
    return meta.getFirstArchivedPosition() == FIRST_LOG_POSITION && !state.snapshots().isEmpty();
  }
}
