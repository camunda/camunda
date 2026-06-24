/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata.stream;

import io.camunda.eventbridge.clustermetadata.processing.CreateTopicProcessor;
import io.camunda.eventbridge.clustermetadata.processing.ReassignTopicProcessor;
import io.camunda.eventbridge.clustermetadata.processing.TopicDeleteProcessor;
import io.camunda.eventbridge.clustermetadata.processing.TopicRegisterProcessor;
import io.camunda.eventbridge.clustermetadata.processing.TopicValidator;
import io.camunda.eventbridge.clustermetadata.record.MetadataRecordValues;
import io.camunda.eventbridge.clustermetadata.record.TopicRecord;
import io.camunda.eventbridge.clustermetadata.state.MetadataColumnFamilies;
import io.camunda.eventbridge.clustermetadata.state.appliers.TopicDeletedApplier;
import io.camunda.eventbridge.clustermetadata.state.appliers.TopicRegisteredApplier;
import io.camunda.eventbridge.clustermetadata.state.topic.DbTopicState;
import io.camunda.eventbridge.clustermetadata.state.topic.TopicMetadata;
import io.camunda.eventbridge.stream.RecordProcessingEngine;
import io.camunda.eventbridge.stream.ReplicatedStream;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.logstreams.log.WriteContext;
import io.camunda.zeebe.logstreams.storage.LogStorage;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.intent.MetadataIntent;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.stream.api.RecordProcessor;
import io.camunda.zeebe.stream.impl.records.RecordValues;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.InstantSource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The metadata group's replicated topic registry: a {@link ReplicatedStream} over the metadata Raft
 * partition's log. Topic register/delete mutations are written as commands; the engine's command
 * processors turn them into committed events that {@link TopicRegisteredApplier} / {@link
 * TopicDeletedApplier} apply to {@link DbTopicState}, and which followers and passive observers
 * replay into their own state.
 *
 * <p>This is the registry-only half of what used to be the {@code CoordinatorStream}: consumer
 * offsets and group metadata stay in the coordinator group; the topic registry lives here in its
 * own Raft group.
 */
public final class MetadataStream extends ReplicatedStream<MetadataColumnFamilies> {

  private static final Logger LOG = LoggerFactory.getLogger(MetadataStream.class);

  private DbTopicState topicState;

  /**
   * @param zeebeDb the state DB, recovered/owned by the {@link
   *     io.camunda.zeebe.broker.system.partitions.StateController} (so snapshots can manage it)
   */
  public MetadataStream(
      final int partitionId,
      final LogStorage logStorage,
      final ActorSchedulingService actorScheduler,
      final ZeebeDb<MetadataColumnFamilies> zeebeDb,
      final InstantSource clock,
      final MeterRegistry meterRegistry) {
    super(partitionId, logStorage, actorScheduler, zeebeDb, clock, meterRegistry);
  }

  @Override
  protected String logName() {
    return "metadata";
  }

  @Override
  protected Supplier<RecordValues> recordValues() {
    return MetadataRecordValues::create;
  }

  @Override
  protected void onStarting() {
    topicState = new DbTopicState(zeebeDb, zeebeDb.createContext());
    // Seed the mirror from durable state before the processor starts (no concurrent access yet),
    // so a replica that recovered topics from a snapshot exposes them even before any replay.
    topicState.seedMirror();
  }

  @Override
  protected RecordProcessor createRecordProcessor() {
    final var validator = new TopicValidator(topicState);
    return new RecordProcessingEngine(
        processors ->
            processors
                .onCommand(
                    MetadataRecordValues.TOPIC_VALUE_TYPE,
                    MetadataIntent.CREATE_TOPIC,
                    new CreateTopicProcessor(processors.writers(), validator))
                .onCommand(
                    MetadataRecordValues.TOPIC_VALUE_TYPE,
                    MetadataIntent.REASSIGN_TOPIC,
                    new ReassignTopicProcessor(processors.writers(), validator))
                .onCommand(
                    MetadataRecordValues.TOPIC_VALUE_TYPE,
                    MetadataIntent.DELETE_TOPIC,
                    new TopicDeleteProcessor(processors.writers(), validator))
                .onCommand(
                    MetadataRecordValues.TOPIC_VALUE_TYPE,
                    MetadataIntent.REGISTER_TOPIC,
                    new TopicRegisterProcessor(processors.writers()))
                .withEventApplier(
                    MetadataIntent.TOPIC_REGISTERED, new TopicRegisteredApplier(topicState))
                .withEventApplier(
                    MetadataIntent.TOPIC_DELETED, new TopicDeletedApplier(topicState)));
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
            .setAssignment(metadata.assignment())
            .setTarget(metadata.target());
    writeTopicCommand(name, command, MetadataIntent.REGISTER_TOPIC);
  }

  /**
   * Client topic requests: written as commands that the processor validates against the replicated
   * registry and replies to after commit (the future completes with the encoded response). Leader
   * only. The manager builds the command (it computes placement, which needs live broker
   * membership).
   */
  public CompletableFuture<byte[]> createTopic(final TopicRecord command) {
    return writeRequest(
        MetadataIntent.CREATE_TOPIC, MetadataRecordValues.TOPIC_VALUE_TYPE, command);
  }

  public CompletableFuture<byte[]> reassignTopic(final TopicRecord command) {
    return writeRequest(
        MetadataIntent.REASSIGN_TOPIC, MetadataRecordValues.TOPIC_VALUE_TYPE, command);
  }

  public CompletableFuture<byte[]> deleteTopic(final TopicRecord command) {
    return writeRequest(
        MetadataIntent.DELETE_TOPIC, MetadataRecordValues.TOPIC_VALUE_TYPE, command);
  }

  /** All registered topics ({@code topicName → metadata}) for failover rebuild / listing. */
  public Map<String, TopicMetadata> topicsSnapshot() {
    return topicState.topicsSnapshot();
  }

  private void writeTopicCommand(
      final String name, final TopicRecord command, final MetadataIntent intent) {
    if (writer == null) {
      return;
    }
    final var metadata =
        new RecordMetadata()
            .recordType(RecordType.COMMAND)
            .valueType(MetadataRecordValues.TOPIC_VALUE_TYPE)
            .intent(intent);
    final var result =
        writer.tryWrite(WriteContext.internal(), LogAppendEntry.of(metadata, command));
    if (result.isLeft()) {
      LOG.warn("Failed to write topic command {} for {}: {}", intent, name, result.getLeft());
    }
  }
}
