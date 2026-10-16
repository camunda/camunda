/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.processing;

import io.camunda.zeebe.backup.logarchive.model.LogArchiveJson;
import io.camunda.zeebe.backup.logarchive.model.LogArchiveKeys;
import io.camunda.zeebe.backup.logarchive.state.DbLogArchiveState;
import io.camunda.zeebe.backup.logarchive.state.LogArchiveMeta;
import io.camunda.zeebe.backup.processing.state.DbCheckpointMetadataState;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
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

  private static final Logger LOG = LoggerFactory.getLogger(LogArchiveRecordsProcessor.class);

  private final LogArchiveProcessorConfig config;
  private volatile @Nullable LogArchiveListener listener;
  private volatile @Nullable LogArchivePublication publication;

  @SuppressWarnings("NullAway.Init")
  private DbLogArchiveState state;

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
    final var checkpointMetadataState =
        new DbCheckpointMetadataState(context.getZeebeDb(), context.getTransactionContext());
    applier = new LogArchiveEventApplier(config.partitionId(), state, checkpointMetadataState);
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
   * row in order, then a root for the latest seq and a seal request if the open chunk is full. A
   * new leader republishes unconditionally; writing the same key again is harmless (I4).
   */
  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    final var meta = state.meta();
    final var currentListener = listener;
    if (currentListener != null) {
      currentListener.onRecovered(meta);
    }
    final var currentPublication = publication;
    if (currentPublication == null || meta.getTimelineId().isEmpty()) {
      return;
    }

    final var timelineId = meta.getTimelineId();
    final var outbox = state.outboxAfter(meta.getLastPublishedSeq());
    for (final var row : outbox) {
      currentPublication.publish(
          new PublishRequest(timelineId, row.seq(), row.commitEntryJson(), null, null));
    }

    final long rootSeq = meta.getNextSeq() - 1;
    final byte[] rootJson = outbox.isEmpty() ? null : rootJson(meta, rootSeq);
    final var seal = pendingSeal(meta);
    if (rootJson != null || seal != null) {
      currentPublication.publish(
          new PublishRequest(timelineId, rootJson != null ? rootSeq : -1, null, rootJson, seal));
    }
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
    final var event = new LogArchiveRecord().setTimelineId(commandValue.getTimelineId());

    switch (intent) {
      case START_TIMELINE ->
          event
              .setSeq(1)
              .setStartIndex(commandValue.getStartIndex())
              .setParentTimelineId(commandValue.getParentTimelineId());
      case ARCHIVE_LOG -> {
        event.setSeq(meta.getNextSeq());
        commandValue.getObjects().forEach(event::addObject);
      }
      case ARCHIVE_SNAPSHOT ->
          event.setSeq(meta.getNextSeq()).setSnapshot(commandValue.getSnapshot());
      case SEAL_INDEX -> event.setSeq(meta.getNextSeq()).setChunk(commandValue.getChunk());
      case CONFIRM_PUBLISHED -> event.setPublishedSeq(commandValue.getPublishedSeq());
      default -> throw new IllegalStateException("Unexpected intent " + intent);
    }

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
    final var request = publishRequestFor(eventIntent, event, newMeta, commitEntryJson);
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
            default -> {}
          }
        });
  }

  private @Nullable PublishRequest publishRequestFor(
      final LogArchiveIntent eventIntent,
      final LogArchiveRecord event,
      final LogArchiveMeta meta,
      final byte @Nullable [] commitEntryJson) {
    if (commitEntryJson == null) {
      return null;
    }
    final long seq = event.getSeq();
    final boolean sealed = eventIntent == LogArchiveIntent.INDEX_SEALED;
    final byte[] rootJson = sealed || seq % config.rootEvery() == 0 ? rootJson(meta, seq) : null;
    final var seal =
        sealed || eventIntent == LogArchiveIntent.LOG_ARCHIVED ? pendingSeal(meta) : null;
    return new PublishRequest(event.getTimelineId(), seq, commitEntryJson, rootJson, seal);
  }

  private byte[] rootJson(final LogArchiveMeta meta, final long seq) {
    return LogArchiveJson.write(
        LogArchiveJsonMapper.rootFile(
            config.partitionId(),
            meta,
            seq,
            state.chunks(),
            state.openObjects(),
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
    final var snapshotId = commandValue.getSnapshot().getId();
    resultBuilder.appendPostCommitTask(
        () -> {
          final var currentListener = listener;
          if (currentListener == null) {
            return;
          }
          switch (intent) {
            case ARCHIVE_LOG -> currentListener.onArchiveLogRejected(archivedIndex);
            case ARCHIVE_SNAPSHOT -> currentListener.onSnapshotRejected(snapshotId);
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
}
