/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.stream;

import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.logstreams.log.WriteContext;
import io.camunda.zeebe.logstreams.storage.LogStorage;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamClock;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.impl.StreamProcessor;
import io.camunda.zeebe.stream.impl.StreamProcessorListener;
import io.camunda.zeebe.stream.impl.StreamProcessorMode;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.InstantSource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The coordinator's replicated state engine: a Zeebe {@link StreamProcessor} over the coordinator
 * Raft partition's log, backed by a {@link ZeebeDb}. Offset commits are written as commands; the
 * {@link OffsetCommitProcessor} applies them to {@link DbOffsetState} and emits committed events.
 *
 * <ul>
 *   <li><b>Leader</b> starts the processor in {@link StreamProcessorMode#PROCESSING} and accepts
 *       {@link #commit} writes.
 *   <li><b>Follower</b> starts it in {@link StreamProcessorMode#REPLAY}; it replays committed
 *       events into its own {@link DbOffsetState}, so every replica holds identical state and a new
 *       leader resumes without loss.
 * </ul>
 *
 * <p>Snapshotting (state persistence + replication + log compaction) is layered on separately via
 * the platform's {@code StateController}/{@code AsyncSnapshotDirector}.
 */
public final class CoordinatorStream {

  private final int partitionId;
  private final LogStorage logStorage;
  private final ActorSchedulingService actorScheduler;
  private final ZeebeDb<EventBridgeColumnFamilies> zeebeDb;
  private final InstantSource clock;
  private final MeterRegistry meterRegistry;

  // Correlates a written commit command (by log position) to the future returned to the caller,
  // completed by the processing listener with the resulting committed offset.
  private final ConcurrentHashMap<Long, CompletableFuture<Long>> pendingCommits =
      new ConcurrentHashMap<>();

  private static final Logger LOG = LoggerFactory.getLogger(CoordinatorStream.class);

  private LogStream logStream;
  private DbOffsetState offsetState;
  private DbGroupMetadataState groupMetadataState;
  private StreamProcessor streamProcessor;
  private LogStreamWriter writer;

  /**
   * @param zeebeDb the state DB, recovered/owned by the {@link
   *     io.camunda.zeebe.broker.system.partitions.StateController} (so snapshots can manage it)
   */
  public CoordinatorStream(
      final int partitionId,
      final LogStorage logStorage,
      final ActorSchedulingService actorScheduler,
      final ZeebeDb<EventBridgeColumnFamilies> zeebeDb,
      final InstantSource clock,
      final MeterRegistry meterRegistry) {
    this.partitionId = partitionId;
    this.logStorage = logStorage;
    this.actorScheduler = actorScheduler;
    this.zeebeDb = zeebeDb;
    this.clock = clock;
    this.meterRegistry = meterRegistry;
  }

  /**
   * Starts the stream processor in the given mode. The returned future completes once the processor
   * is <em>opened</em> — which is before the log has been replayed. To run logic against fully
   * replayed state (e.g. the leader rebuilding its consumer-group registry), register a {@code
   * onRecovered} callback: it fires after replay completes and before processing begins, and only
   * in {@link StreamProcessorMode#PROCESSING} (followers stay in replay and never invoke it).
   *
   * @param onRecovered invoked on the processor's actor thread once replay has finished; may be
   *     {@code null}
   */
  public ActorFuture<Void> start(final StreamProcessorMode mode, final Runnable onRecovered) {
    logStream =
        LogStream.builder()
            .withLogStorage(logStorage)
            .withLogName("coordinator-" + partitionId)
            .withPartitionId(partitionId)
            .withClock(clock)
            .withMeterRegistry(meterRegistry)
            .build();

    offsetState = new DbOffsetState(zeebeDb, zeebeDb.createContext());
    groupMetadataState = new DbGroupMetadataState(zeebeDb, zeebeDb.createContext());

    final var builder =
        StreamProcessor.builder()
            .meterRegistry(meterRegistry)
            .clock(StreamClock.controllable(clock))
            .logStream(logStream)
            .zeebeDb(zeebeDb)
            .actorSchedulingService(actorScheduler)
            .recordProcessors(
                List.of(
                    new OffsetCommitProcessor(offsetState),
                    new GroupMetadataProcessor(groupMetadataState)))
            .recordValues(EventBridgeRecordValues::create)
            .commandResponseWriter(new NoopCommandResponseWriter())
            .partitionCommandSender(new NoopInterPartitionCommandSender())
            .streamProcessorMode(mode)
            .listener(new CommitCompletionListener());

    if (onRecovered != null) {
      builder.addLifecycleListener(
          new StreamProcessorLifecycleAware() {
            @Override
            public void onRecovered(final ReadonlyStreamProcessorContext context) {
              onRecovered.run();
            }
          });
    }

    streamProcessor = builder.build();
    writer = logStream.newLogStreamWriter();
    return streamProcessor.openAsync(false);
  }

  /**
   * Writes an offset-commit command to the replicated log; the returned future completes with the
   * resulting (monotonic) committed position once the command has been processed. Leader only.
   */
  public CompletableFuture<Long> commit(
      final String groupId, final int partitionId, final long position) {
    final var command =
        new OffsetCommitRecord()
            .setGroupId(groupId)
            .setPartitionId(partitionId)
            .setOffset(position);
    final var metadata =
        new RecordMetadata()
            .recordType(RecordType.COMMAND)
            .valueType(EventBridgeRecordValues.OFFSET_VALUE_TYPE)
            .intent(CoordinatorIntent.COMMIT_OFFSET);

    final var result =
        writer.tryWrite(WriteContext.internal(), LogAppendEntry.of(metadata, command));
    if (result.isLeft()) {
      return CompletableFuture.failedFuture(
          new IllegalStateException("Failed to write offset commit: " + result.getLeft()));
    }

    final long commandPosition = result.get();
    final var future =
        pendingCommits.compute(
            commandPosition,
            (pos, existing) -> existing != null ? existing : new CompletableFuture<>());
    future.whenComplete((r, e) -> pendingCommits.remove(commandPosition));
    return future;
  }

  /**
   * Returns the committed offsets for a group (partition → position), read from replicated state.
   */
  public Map<Integer, Long> committedOffsets(final String groupId) {
    return offsetState.getOffsets(groupId);
  }

  /**
   * Replicates a group's membership/assignment (encoded via {@link GroupMetadataCodec}) through the
   * stream so it survives coordinator failover. Best-effort: written on each rebalance. Leader
   * only.
   */
  public void replicateGroupMetadata(final String groupId, final String payload) {
    if (writer == null) {
      return;
    }
    final var command = new GroupMetadataRecord().setGroupId(groupId).setPayload(payload);
    final var metadata =
        new RecordMetadata()
            .recordType(RecordType.COMMAND)
            .valueType(EventBridgeRecordValues.GROUP_METADATA_VALUE_TYPE)
            .intent(CoordinatorIntent.REBALANCE_GROUP);
    final var result =
        writer.tryWrite(WriteContext.internal(), LogAppendEntry.of(metadata, command));
    if (result.isLeft()) {
      LOG.warn("Failed to replicate group metadata for {}: {}", groupId, result.getLeft());
    }
  }

  /** All groups' replicated metadata ({@code groupId → encoded payload}) for failover rebuild. */
  public Map<String, String> groupMetadataSnapshot() {
    return groupMetadataState.readAll();
  }

  /** The underlying stream processor, e.g. for the snapshot director. */
  public StreamProcessor streamProcessor() {
    return streamProcessor;
  }

  /** The log stream, e.g. to seed the snapshot director's commit position from the log tip. */
  public LogStream logStream() {
    return logStream;
  }

  /**
   * Stops the processor and log stream. The {@code ZeebeDb} is owned/closed by the StateController.
   */
  public ActorFuture<Void> stop() {
    final ActorFuture<Void> closed = streamProcessor.closeAsync();
    closed.onComplete(
        (ok, error) -> {
          if (logStream != null) {
            logStream.close();
          }
        });
    return closed;
  }

  /** Completes the pending commit future once its command has been processed. */
  private final class CommitCompletionListener implements StreamProcessorListener {
    @Override
    public void onProcessed(final TypedRecord<?> processedCommand) {
      if (!(processedCommand.getValue() instanceof final OffsetCommitRecord command)) {
        return;
      }
      final long committed = offsetState.getOffset(command.getGroupId(), command.getPartitionId());
      pendingCommits
          .compute(
              processedCommand.getPosition(),
              (pos, existing) -> existing != null ? existing : new CompletableFuture<>())
          .complete(committed);
    }
  }
}
