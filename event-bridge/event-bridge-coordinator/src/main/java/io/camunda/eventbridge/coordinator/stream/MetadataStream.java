/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.coordinator.stream;

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
import io.camunda.zeebe.stream.impl.StreamProcessor;
import io.camunda.zeebe.stream.impl.StreamProcessorMode;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.InstantSource;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The metadata group's replicated topic registry: a Zeebe {@link StreamProcessor} over the metadata
 * Raft partition's log, backed by a {@link ZeebeDb}. Topic register/delete mutations are written as
 * commands; the {@link TopicProcessor} applies them to {@link DbTopicState} and emits committed
 * events.
 *
 * <ul>
 *   <li><b>Leader</b> starts the processor in {@link StreamProcessorMode#PROCESSING} and accepts
 *       {@link #registerTopic}/{@link #deleteTopic} writes.
 *   <li><b>Follower / passive observer</b> starts it in {@link StreamProcessorMode#REPLAY}; it
 *       replays committed events into its own {@link DbTopicState}, so every replica (and, from M3,
 *       every broker observing the metadata group) holds the identical topic set.
 * </ul>
 *
 * <p>This is the registry-only half of what used to be the {@code CoordinatorStream}: consumer
 * offsets and group metadata stay in the coordinator group; the topic registry lives here in its
 * own Raft group.
 */
public final class MetadataStream {

  private static final Logger LOG = LoggerFactory.getLogger(MetadataStream.class);

  private final int partitionId;
  private final LogStorage logStorage;
  private final ActorSchedulingService actorScheduler;
  private final ZeebeDb<EventBridgeColumnFamilies> zeebeDb;
  private final InstantSource clock;
  private final MeterRegistry meterRegistry;

  private LogStream logStream;
  private DbTopicState topicState;
  private StreamProcessor streamProcessor;
  private LogStreamWriter writer;

  /**
   * @param zeebeDb the state DB, recovered/owned by the {@link
   *     io.camunda.zeebe.broker.system.partitions.StateController} (so snapshots can manage it)
   */
  public MetadataStream(
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
   * replayed state (e.g. the leader re-reading the topic registry), register a {@code onRecovered}
   * callback: it fires after replay completes and before processing begins, and only in {@link
   * StreamProcessorMode#PROCESSING} (followers stay in replay and never invoke it).
   *
   * @param onRecovered invoked on the processor's actor thread once replay has finished; may be
   *     {@code null}
   */
  public ActorFuture<Void> start(final StreamProcessorMode mode, final Runnable onRecovered) {
    logStream =
        LogStream.builder()
            .withLogStorage(logStorage)
            .withLogName("metadata-" + partitionId)
            .withPartitionId(partitionId)
            .withClock(clock)
            .withMeterRegistry(meterRegistry)
            .build();

    topicState = new DbTopicState(zeebeDb, zeebeDb.createContext());

    final var builder =
        StreamProcessor.builder()
            .meterRegistry(meterRegistry)
            .clock(StreamClock.controllable(clock))
            .logStream(logStream)
            .zeebeDb(zeebeDb)
            .actorSchedulingService(actorScheduler)
            .recordProcessors(List.of(new TopicProcessor(topicState)))
            .recordValues(EventBridgeRecordValues::create)
            .commandResponseWriter(new NoopCommandResponseWriter())
            .partitionCommandSender(new NoopInterPartitionCommandSender())
            .streamProcessorMode(mode);

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
   * Registers (creates or updates) a topic's desired configuration in the replicated registry.
   * Leader only.
   */
  public void registerTopic(
      final String name,
      final int partitionCount,
      final int replicationFactor,
      final TopicMetadata.TopicStatus status,
      final Map<Integer, List<Integer>> assignment) {
    registerTopic(name, new TopicMetadata(partitionCount, replicationFactor, status, assignment));
  }

  /** Registers a topic's full desired configuration (committed assignment + in-flight target). */
  public void registerTopic(final String name, final TopicMetadata metadata) {
    final var command =
        new TopicRecord()
            .setName(name)
            .setOp(TopicRecord.OP_REGISTER)
            .setPartitionCount(metadata.partitionCount())
            .setReplicationFactor(metadata.replicationFactor())
            .setStatus(metadata.status())
            .setAssignment(TopicMetadata.encodeAssignment(metadata.assignment()))
            .setTarget(TopicMetadata.encodeAssignment(metadata.target()));
    writeTopicCommand(name, command, CoordinatorIntent.REGISTER_TOPIC);
  }

  /** Removes a topic from the replicated registry. Leader only. */
  public void deleteTopic(final String name) {
    final var command = new TopicRecord().setName(name).setOp(TopicRecord.OP_DELETE);
    writeTopicCommand(name, command, CoordinatorIntent.DELETE_TOPIC);
  }

  /** All registered topics ({@code topicName → metadata}) for failover rebuild / listing. */
  public Map<String, TopicMetadata> topicsSnapshot() {
    return topicState.readAll();
  }

  private void writeTopicCommand(
      final String name, final TopicRecord command, final CoordinatorIntent intent) {
    if (writer == null) {
      return;
    }
    final var metadata =
        new RecordMetadata()
            .recordType(RecordType.COMMAND)
            .valueType(EventBridgeRecordValues.TOPIC_VALUE_TYPE)
            .intent(intent);
    final var result =
        writer.tryWrite(WriteContext.internal(), LogAppendEntry.of(metadata, command));
    if (result.isLeft()) {
      LOG.warn("Failed to write topic command {} for {}: {}", intent, name, result.getLeft());
    }
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
}
