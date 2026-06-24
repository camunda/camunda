/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.stream;

import io.camunda.eventbridge.stream.RecordProcessingEngine;
import io.camunda.eventbridge.stream.ReplicatedStream;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.logstreams.log.WriteContext;
import io.camunda.zeebe.logstreams.storage.LogStorage;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.stream.api.RecordProcessor;
import io.camunda.zeebe.stream.impl.records.RecordValues;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.InstantSource;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The coordinator's replicated state engine: a {@link ReplicatedStream} over the coordinator Raft
 * partition's log. Offset commits and group-metadata updates are written as commands; the {@link
 * OffsetCommitProcessor} and {@link GroupMetadataProcessor} validate and apply them to {@link
 * DbOffsetState} / {@link DbGroupMetadataState} and emit committed events, which followers replay
 * into their own state.
 */
public final class CoordinatorStream extends ReplicatedStream<EventBridgeColumnFamilies> {

  private static final Logger LOG = LoggerFactory.getLogger(CoordinatorStream.class);

  private DbOffsetState offsetState;
  private DbGroupMetadataState groupMetadataState;

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
    super(partitionId, logStorage, actorScheduler, zeebeDb, clock, meterRegistry);
  }

  @Override
  protected String logName() {
    return "coordinator";
  }

  @Override
  protected Supplier<RecordValues> recordValues() {
    return EventBridgeRecordValues::create;
  }

  @Override
  protected void onStarting() {
    offsetState = new DbOffsetState(zeebeDb, zeebeDb.createContext());
    groupMetadataState = new DbGroupMetadataState(zeebeDb, zeebeDb.createContext());
  }

  @Override
  protected RecordProcessor createRecordProcessor() {
    return new RecordProcessingEngine(
        processors ->
            processors
                .onCommand(
                    EventBridgeRecordValues.OFFSET_VALUE_TYPE,
                    CoordinatorIntent.COMMIT_OFFSET,
                    new OffsetCommitProcessor(
                        processors.writers(),
                        offsetState,
                        new OffsetCommitValidator(groupMetadataState)))
                .onCommand(
                    EventBridgeRecordValues.GROUP_METADATA_VALUE_TYPE,
                    CoordinatorIntent.REBALANCE_GROUP,
                    new GroupMetadataProcessor(processors.writers()))
                .withEventApplier(
                    CoordinatorIntent.OFFSET_COMMITTED, new OffsetCommittedApplier(offsetState))
                .withEventApplier(
                    CoordinatorIntent.GROUP_METADATA_COMMITTED,
                    new GroupMetadataCommittedApplier(groupMetadataState)));
  }

  /**
   * Writes an offset-commit command to the replicated log; the returned future completes with the
   * encoded {@code CommitOffsetResponse} once the command has been processed and committed (the
   * processor validates the commit, applies it, and stages the reply). Leader only.
   */
  public CompletableFuture<byte[]> commit(final OffsetCommitRecord command) {
    return writeRequest(
        CoordinatorIntent.COMMIT_OFFSET, EventBridgeRecordValues.OFFSET_VALUE_TYPE, command);
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
}
