/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.processing;

import io.camunda.zeebe.backup.logarchive.model.LogArchiveKeys;
import io.camunda.zeebe.backup.logarchive.state.DbLogArchiveState;
import io.camunda.zeebe.backup.logarchive.state.LogArchiveMeta;
import io.camunda.zeebe.backup.processing.CheckpointBackupConfirmedApplier;
import io.camunda.zeebe.backup.processing.CheckpointCreatedEventApplier;
import io.camunda.zeebe.backup.processing.state.DbBackupRangeState;
import io.camunda.zeebe.backup.processing.state.DbCheckpointMetadataState;
import io.camunda.zeebe.backup.processing.state.DbCheckpointState;
import io.camunda.zeebe.db.AccessMetricsConfiguration;
import io.camunda.zeebe.db.AccessMetricsConfiguration.Kind;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.management.ArchivedSnapshot;
import io.camunda.zeebe.protocol.impl.record.value.management.CheckpointRecord;
import io.camunda.zeebe.protocol.impl.record.value.management.IndexChunk;
import io.camunda.zeebe.protocol.impl.record.value.management.LogArchiveRecord;
import io.camunda.zeebe.protocol.impl.record.value.management.LogObject;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.management.LogArchiveIntent;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import io.camunda.zeebe.stream.api.PostCommitTask;
import io.camunda.zeebe.stream.api.ProcessingResponse;
import io.camunda.zeebe.stream.api.ProcessingResult;
import io.camunda.zeebe.stream.api.ProcessingResultBuilder;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamClock;
import io.camunda.zeebe.stream.api.records.ImmutableRecordBatch;
import io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService;
import io.camunda.zeebe.stream.impl.RecordProcessorContextImpl;
import io.camunda.zeebe.stream.impl.records.RecordBatch;
import io.camunda.zeebe.stream.impl.state.DbKeyGenerator;
import io.camunda.zeebe.util.Either;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.File;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.agrona.concurrent.UnsafeBuffer;
import org.mockito.Mockito;

/**
 * One partition's worth of log archive processing on a real RocksDB: a processor, the state it
 * writes, and recordings of everything it emits. Events are captured serialized, as the log would
 * hold them.
 */
final class LogArchiveHarness implements AutoCloseable {

  static final String TIMELINE = "tl-0";

  final ZeebeDb<ZbColumnFamilies> db;
  final LogArchiveRecordsProcessor processor;
  final DbCheckpointMetadataState checkpoints;
  final RecordingListener listener = new RecordingListener();
  final List<PublishRequest> published = new ArrayList<>();
  final List<CapturedEvent> events = new ArrayList<>();
  final List<CapturedEvent> rejections = new ArrayList<>();

  /** Every event in log order: the log archive events and the checkpoint events in between. */
  final List<LoggedEvent> log = new ArrayList<>();

  private final TransactionContext transactionContext;
  private final CheckpointCreatedEventApplier checkpointCreatedApplier;
  private final CheckpointBackupConfirmedApplier checkpointConfirmedApplier;
  private long nextPosition = 1000;

  LogArchiveHarness(final File directory, final LogArchiveProcessorConfig config) {
    db =
        new ZeebeRocksDbFactory<ZbColumnFamilies>(
                new RocksDbConfiguration(),
                new ConsistencyChecksSettings(true, true),
                new AccessMetricsConfiguration(Kind.NONE),
                SimpleMeterRegistry::new)
            .createDb(directory);
    transactionContext = db.createContext();
    final var context =
        new RecordProcessorContextImpl(
            config.partitionId(),
            Mockito.mock(ProcessingScheduleService.class),
            db,
            transactionContext,
            null,
            new DbKeyGenerator(config.partitionId(), db, transactionContext),
            StreamClock.controllable(InstantSource.system()),
            new SimpleMeterRegistry());
    processor = new LogArchiveRecordsProcessor(config);
    processor.setListener(listener);
    processor.setPublication(published::add);
    processor.init(context);
    checkpoints = new DbCheckpointMetadataState(db, transactionContext);
    final var checkpointState = new DbCheckpointState(db, transactionContext);
    // as the broker wires it with the log archive and continuous backups enabled
    checkpointCreatedApplier =
        new CheckpointCreatedEventApplier(
            checkpointState,
            checkpoints,
            new DbLogArchiveState(db, transactionContext),
            Set.of(),
            true,
            true);
    checkpointConfirmedApplier =
        new CheckpointBackupConfirmedApplier(
            checkpointState, checkpoints, new DbBackupRangeState(db, transactionContext), true);
  }

  /** A reader on its own transaction context, so that reads never see a stale snapshot. */
  DbLogArchiveState state() {
    return new DbLogArchiveState(db, db.createContext());
  }

  /** Processes the command and runs the post commit tasks, as the stream processor would. */
  ProcessingResult process(final LogArchiveIntent intent, final LogArchiveRecord command) {
    final var builder = new Builder();
    final var record =
        new MockTypedLogArchiveRecord(nextPosition++, -1, intent, RecordType.COMMAND, command);
    final var result = new ProcessingResult[1];
    // as the stream processor does: all of processing happens in one transaction
    transactionContext.runInTransaction(() -> result[0] = processor.process(record, builder));
    builder.postCommitTasks.forEach(PostCommitTask::flush);
    for (final var event : builder.captured) {
      final var captured =
          new CapturedEvent(
              event.intent(), event.type(), event.value(), record.position(), event.rejection());
      if (event.type() == RecordType.EVENT) {
        events.add(captured);
        log.add(captured);
      } else {
        rejections.add(captured);
      }
    }
    return result[0];
  }

  /** Accepts the command and returns the event it produced. */
  LogArchiveRecord accept(final LogArchiveIntent intent, final LogArchiveRecord command) {
    final int before = events.size();
    process(intent, command);
    if (events.size() != before + 1) {
      throw new AssertionError("Expected %s to be accepted, but it was rejected".formatted(intent));
    }
    return events.getLast().value();
  }

  /**
   * Applies a checkpoint {@code CREATED} event with the real applier, as the checkpoint processor
   * does when processing and when replaying.
   */
  void createCheckpoint(
      final long checkpointId, final long checkpointPosition, final CheckpointType type) {
    final var created = new CheckpointCreated(checkpointId, checkpointPosition, type);
    apply(created);
    log.add(created);
  }

  /** Applies a checkpoint {@code CONFIRMED_BACKUP} event with the real applier. */
  void confirmBackup(
      final long checkpointId, final long checkpointPosition, final CheckpointType type) {
    final var confirmed = new BackupConfirmed(checkpointId, checkpointPosition, type);
    apply(confirmed);
    log.add(confirmed);
  }

  /** Replays every event of the given log in order, as a follower would. */
  void replayLog(final List<LoggedEvent> events) {
    for (final var event : events) {
      switch (event) {
        case final CapturedEvent captured -> replay(captured);
        default -> apply(event);
      }
    }
  }

  private void apply(final LoggedEvent event) {
    switch (event) {
      case final CheckpointCreated created ->
          transactionContext.runInTransaction(
              () -> checkpointCreatedApplier.apply(created.toRecord(), 0));
      case final BackupConfirmed confirmed ->
          transactionContext.runInTransaction(
              () -> checkpointConfirmedApplier.apply(confirmed.toRecord(), 0));
      case final CapturedEvent captured -> throw new IllegalArgumentException("" + captured);
    }
  }

  void replay(final CapturedEvent event) {
    final var record =
        new MockTypedLogArchiveRecord(
            nextPosition++,
            event.commandPosition(),
            event.intent(),
            RecordType.EVENT,
            copy(event.value()));
    transactionContext.runInTransaction(() -> processor.replay(record));
  }

  void recover() {
    processor.onRecovered(Mockito.mock(ReadonlyStreamProcessorContext.class));
  }

  // ---- command and fixture builders -----------------------------------------------------------

  static LogArchiveRecord startTimeline(final String timelineId, final long startIndex) {
    return new LogArchiveRecord()
        .setTimelineId(timelineId)
        .setStartIndex(startIndex)
        .setParentTimelineId("parent");
  }

  static LogObject object(
      final long firstIndex,
      final long lastIndex,
      final long firstPosition,
      final long lastPosition) {
    return new LogObject()
        .setKey(LogArchiveKeys.dataKey(firstIndex, lastIndex, 1))
        .setTerm(1)
        .setFirstIndex(firstIndex)
        .setLastIndex(lastIndex)
        .setFirstPosition(firstPosition)
        .setLastPosition(lastPosition)
        .setEntryCount(lastIndex - firstIndex + 1)
        .setSizeBytes(100)
        .setSha256("ab".repeat(32));
  }

  /** An object holding a single entry whose position equals its index. */
  static LogObject object(final long index) {
    return object(index, index, index, index);
  }

  static LogArchiveRecord archiveLog(final String timelineId, final LogObject... objects) {
    final var command = new LogArchiveRecord().setTimelineId(timelineId);
    for (final var object : objects) {
      command.addObject(object);
    }
    return command;
  }

  static LogArchiveRecord archiveLog(final LogObject... objects) {
    return archiveLog(TIMELINE, objects);
  }

  static ArchivedSnapshot snapshot(final String id, final long index) {
    return new ArchivedSnapshot()
        .setId(id)
        .setIndex(index)
        .setTerm(1)
        .setProcessedPosition(index)
        .setFileCount(3)
        .setSizeBytes(1000);
  }

  static LogArchiveRecord archiveSnapshot(final ArchivedSnapshot snapshot) {
    return new LogArchiveRecord().setTimelineId(TIMELINE).setSnapshot(snapshot);
  }

  static LogArchiveRecord sealIndex(final IndexChunk chunk) {
    return new LogArchiveRecord().setTimelineId(TIMELINE).setChunk(chunk);
  }

  static LogArchiveRecord confirmPublished(final long publishedSeq) {
    return new LogArchiveRecord().setTimelineId(TIMELINE).setPublishedSeq(publishedSeq);
  }

  static LogArchiveRecord copy(final LogArchiveRecord record) {
    final var buffer = new UnsafeBuffer(new byte[record.getLength()]);
    record.write(buffer, 0);
    final var copy = new LogArchiveRecord();
    copy.wrap(buffer, 0, buffer.capacity());
    return copy;
  }

  /** Renders everything in the state in a form that is equal iff the state is equal. */
  String fingerprint() {
    final var builder = new StringBuilder();
    final var state = state();
    builder.append(state.meta()).append('\n');
    state
        .openObjects()
        .forEach(
            open ->
                builder
                    .append(LogArchiveJsonMapper.logObject(open.object()))
                    .append(
                        open.checkpoints().stream().map(LogArchiveJsonMapper::checkpoint).toList())
                    .append('\n'));
    state.chunks().forEach(chunk -> builder.append(LogArchiveJsonMapper.chunk(chunk)).append('\n'));
    state
        .snapshots()
        .forEach(snapshot -> builder.append(LogArchiveJsonMapper.snapshot(snapshot)).append('\n'));
    state
        .pendingCheckpointsUpTo(Long.MAX_VALUE)
        .forEach(
            checkpoint -> builder.append(LogArchiveJsonMapper.checkpoint(checkpoint)).append('\n'));
    state
        .outboxAfter(-1)
        .forEach(
            row ->
                builder
                    .append(row.seq())
                    .append(' ')
                    .append(new String(row.commitEntryJson()))
                    .append('\n'));
    return builder.toString();
  }

  @Override
  public void close() throws Exception {
    db.close();
  }

  sealed interface LoggedEvent permits CapturedEvent, CheckpointCreated, BackupConfirmed {}

  record CapturedEvent(
      Intent intent,
      RecordType type,
      LogArchiveRecord value,
      long commandPosition,
      RejectionType rejection)
      implements LoggedEvent {}

  record CheckpointCreated(long checkpointId, long checkpointPosition, CheckpointType type)
      implements LoggedEvent {
    CheckpointRecord toRecord() {
      return checkpointRecord(checkpointId, checkpointPosition, type);
    }
  }

  record BackupConfirmed(long checkpointId, long checkpointPosition, CheckpointType type)
      implements LoggedEvent {
    CheckpointRecord toRecord() {
      return checkpointRecord(checkpointId, checkpointPosition, type);
    }
  }

  private static CheckpointRecord checkpointRecord(
      final long checkpointId, final long checkpointPosition, final CheckpointType type) {
    return new CheckpointRecord()
        .setCheckpointId(checkpointId)
        .setCheckpointPosition(checkpointPosition)
        .setCheckpointType(type);
  }

  record Appended(
      Intent intent, RecordType type, LogArchiveRecord value, RejectionType rejection) {}

  static final class RecordingListener implements LogArchiveListener {
    final List<String> calls = new ArrayList<>();
    final List<NewestSnapshot> newestSnapshotsOnRecovery = new ArrayList<>();

    record NewestSnapshot(String id, long processedPosition) {}

    @Override
    public void onTimelineStarted(final String timelineId) {
      calls.add("timelineStarted " + timelineId);
    }

    @Override
    public void onLogArchived(final long archivedIndex, final long archivedPosition) {
      calls.add("logArchived " + archivedIndex + " " + archivedPosition);
    }

    @Override
    public void onArchiveLogRejected(final long archivedIndex) {
      calls.add("archiveLogRejected " + archivedIndex);
    }

    @Override
    public void onSnapshotArchived(final String snapshotId) {
      calls.add("snapshotArchived " + snapshotId);
    }

    @Override
    public void onSnapshotRejected(final String snapshotId) {
      calls.add("snapshotRejected " + snapshotId);
    }

    @Override
    public void onIndexSealed(final long chunkNo) {
      calls.add("indexSealed " + chunkNo);
    }

    @Override
    public void onPublished(final long publishedSeq) {
      calls.add("published " + publishedSeq);
    }

    @Override
    public void onCommandRejected(
        final LogArchiveIntent command, final RejectionType type, final String reason) {
      calls.add("rejected " + command + " " + type);
    }

    @Override
    public void onRecovered(final LogArchiveMeta meta) {
      calls.add("recovered " + meta.getNextSeq());
    }

    @Override
    public void onNewestArchivedSnapshotRecovered(
        final String snapshotId, final long processedPosition) {
      newestSnapshotsOnRecovery.add(new NewestSnapshot(snapshotId, processedPosition));
    }
  }

  private static final class Builder implements ProcessingResultBuilder {
    final List<Appended> captured = new ArrayList<>();
    final List<PostCommitTask> postCommitTasks = new ArrayList<>();

    @Override
    public Either<RuntimeException, ProcessingResultBuilder> appendRecordReturnEither(
        final long key, final RecordValue value, final RecordMetadata metadata) {
      captured.add(
          new Appended(
              metadata.getIntent(),
              metadata.getRecordType(),
              copy((LogArchiveRecord) value),
              metadata.getRejectionType()));
      return Either.right(this);
    }

    @Override
    public ProcessingResultBuilder withResponse(
        final RecordType type,
        final long key,
        final Intent intent,
        final UnpackedObject value,
        final ValueType valueType,
        final RejectionType rejectionType,
        final String rejectionReason,
        final long requestId,
        final int requestStreamId) {
      return this;
    }

    @Override
    public ProcessingResultBuilder appendPostCommitTask(final PostCommitTask task) {
      postCommitTasks.add(task);
      return this;
    }

    @Override
    public ProcessingResultBuilder resetPostCommitTasks() {
      postCommitTasks.clear();
      return this;
    }

    @Override
    public ProcessingResult build() {
      return new ProcessingResult() {
        @Override
        public ImmutableRecordBatch getRecordBatch() {
          return RecordBatch.empty();
        }

        @Override
        public Optional<ProcessingResponse> getProcessingResponse() {
          return Optional.empty();
        }

        @Override
        public List<PostCommitTask> getPostCommitTasks() {
          return postCommitTasks;
        }

        @Override
        public boolean isEmpty() {
          return captured.isEmpty();
        }
      };
    }

    @Override
    public boolean canWriteEventOfLength(final int eventLength) {
      return false;
    }
  }
}
