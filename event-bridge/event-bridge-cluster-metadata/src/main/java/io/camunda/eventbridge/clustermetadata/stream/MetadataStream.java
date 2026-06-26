/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata.stream;

import io.camunda.eventbridge.clustermetadata.placement.PlacementStrategy;
import io.camunda.eventbridge.clustermetadata.placement.SpreadPlacement;
import io.camunda.eventbridge.clustermetadata.processing.BrokerEvictionTask;
import io.camunda.eventbridge.clustermetadata.processing.BrokerTransitionValidator;
import io.camunda.eventbridge.clustermetadata.processing.CreateTopicProcessor;
import io.camunda.eventbridge.clustermetadata.processing.DeregisterBrokerProcessor;
import io.camunda.eventbridge.clustermetadata.processing.DrainBrokerProcessor;
import io.camunda.eventbridge.clustermetadata.processing.FenceBrokerProcessor;
import io.camunda.eventbridge.clustermetadata.processing.PlacementHealTask;
import io.camunda.eventbridge.clustermetadata.processing.ReassignTopicProcessor;
import io.camunda.eventbridge.clustermetadata.processing.RegisterBrokerProcessor;
import io.camunda.eventbridge.clustermetadata.processing.ReportPartitionLeaderProcessor;
import io.camunda.eventbridge.clustermetadata.processing.TopicDeleteProcessor;
import io.camunda.eventbridge.clustermetadata.processing.TopicRegisterProcessor;
import io.camunda.eventbridge.clustermetadata.processing.TopicValidator;
import io.camunda.eventbridge.clustermetadata.record.BrokerRecord;
import io.camunda.eventbridge.clustermetadata.record.MetadataRecordValues;
import io.camunda.eventbridge.clustermetadata.record.TopicRecord;
import io.camunda.eventbridge.clustermetadata.session.BrokerLivenessMirror;
import io.camunda.eventbridge.clustermetadata.state.MetadataColumnFamilies;
import io.camunda.eventbridge.clustermetadata.state.appliers.BrokerDeregisteredApplier;
import io.camunda.eventbridge.clustermetadata.state.appliers.BrokerDrainingApplier;
import io.camunda.eventbridge.clustermetadata.state.appliers.BrokerFencedApplier;
import io.camunda.eventbridge.clustermetadata.state.appliers.BrokerRegisteredApplier;
import io.camunda.eventbridge.clustermetadata.state.appliers.PartitionLeaderReportedApplier;
import io.camunda.eventbridge.clustermetadata.state.appliers.TopicDeletedApplier;
import io.camunda.eventbridge.clustermetadata.state.appliers.TopicRegisteredApplier;
import io.camunda.eventbridge.clustermetadata.state.broker.BrokerQueryService;
import io.camunda.eventbridge.clustermetadata.state.broker.DbBrokerState;
import io.camunda.eventbridge.clustermetadata.state.topic.DbTopicState;
import io.camunda.eventbridge.clustermetadata.state.topic.TopicMetadata;
import io.camunda.eventbridge.clustermetadata.state.topic.TopicQueryService;
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
import java.time.Duration;
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

  // Liveness sweep cadence + how long without a broker heartbeat before it is fenced.
  private static final Duration BROKER_EVICTION_INTERVAL = Duration.ofSeconds(1);
  private static final Duration BROKER_SESSION_TIMEOUT = Duration.ofSeconds(10);
  // Re-placement sweep cadence (heals topic placement off fenced/draining brokers).
  private static final Duration PLACEMENT_HEAL_INTERVAL = Duration.ofSeconds(1);

  private final InstantSource clock;
  private final Supplier<List<Integer>> raftMembers;
  private final PlacementStrategy placement = new SpreadPlacement();

  private DbTopicState topicState;
  private DbBrokerState brokerState;
  private BrokerLivenessMirror brokerLiveness;

  /**
   * @param zeebeDb the state DB, recovered/owned by the {@link
   *     io.camunda.zeebe.broker.system.partitions.StateController} (so snapshots can manage it)
   * @param raftMembers the metadata-group Raft membership, used as the placement broker set only
   *     until brokers have registered through the liveness FSM (the bootstrap fallback)
   */
  public MetadataStream(
      final int partitionId,
      final LogStorage logStorage,
      final ActorSchedulingService actorScheduler,
      final ZeebeDb<MetadataColumnFamilies> zeebeDb,
      final InstantSource clock,
      final MeterRegistry meterRegistry,
      final Supplier<List<Integer>> raftMembers) {
    super(partitionId, logStorage, actorScheduler, zeebeDb, clock, meterRegistry);
    this.clock = clock;
    this.raftMembers = raftMembers;
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
    brokerState = new DbBrokerState(zeebeDb, zeebeDb.createContext());
    brokerLiveness = new BrokerLivenessMirror();
  }

  @Override
  protected RecordProcessor createRecordProcessor() {
    final var validator = new TopicValidator(topicState);
    final var brokerTransitions = new BrokerTransitionValidator(brokerState);
    // Placement targets registered, unfenced brokers, read from the replicated broker registry on
    // the processing actor — the broker liveness FSM is the source of placeable brokers. Until any
    // broker has registered (bootstrap), fall back to the Raft membership so placement still works.
    final Supplier<List<Integer>> activeBrokers =
        () -> {
          final var active = brokerState.activeBrokers();
          return active.isEmpty() ? raftMembers.get() : active;
        };
    return new RecordProcessingEngine(
        processors ->
            processors
                .onCommand(
                    MetadataRecordValues.TOPIC_VALUE_TYPE,
                    MetadataIntent.CREATE_TOPIC,
                    new CreateTopicProcessor(
                        processors.writers(), validator, placement, activeBrokers))
                .onCommand(
                    MetadataRecordValues.TOPIC_VALUE_TYPE,
                    MetadataIntent.REASSIGN_TOPIC,
                    new ReassignTopicProcessor(
                        processors.writers(), validator, topicState, placement, activeBrokers))
                .onCommand(
                    MetadataRecordValues.TOPIC_VALUE_TYPE,
                    MetadataIntent.DELETE_TOPIC,
                    new TopicDeleteProcessor(processors.writers(), validator))
                .onCommand(
                    MetadataRecordValues.TOPIC_VALUE_TYPE,
                    MetadataIntent.REGISTER_TOPIC,
                    new TopicRegisterProcessor(processors.writers()))
                .onCommand(
                    MetadataRecordValues.TOPIC_VALUE_TYPE,
                    MetadataIntent.REPORT_PARTITION_LEADER,
                    new ReportPartitionLeaderProcessor(processors.writers(), validator, topicState))
                .onCommand(
                    MetadataRecordValues.BROKER_VALUE_TYPE,
                    MetadataIntent.REGISTER_BROKER,
                    new RegisterBrokerProcessor(processors.writers(), brokerState))
                .onCommand(
                    MetadataRecordValues.BROKER_VALUE_TYPE,
                    MetadataIntent.FENCE_BROKER,
                    new FenceBrokerProcessor(processors.writers(), brokerState, brokerTransitions))
                .onCommand(
                    MetadataRecordValues.BROKER_VALUE_TYPE,
                    MetadataIntent.DRAIN_BROKER,
                    new DrainBrokerProcessor(processors.writers(), brokerState, brokerTransitions))
                .onCommand(
                    MetadataRecordValues.BROKER_VALUE_TYPE,
                    MetadataIntent.DEREGISTER_BROKER,
                    new DeregisterBrokerProcessor(processors.writers(), brokerTransitions))
                .withEventApplier(
                    MetadataIntent.TOPIC_REGISTERED, new TopicRegisteredApplier(topicState))
                .withEventApplier(MetadataIntent.TOPIC_DELETED, new TopicDeletedApplier(topicState))
                .withEventApplier(
                    MetadataIntent.PARTITION_LEADER_REPORTED,
                    new PartitionLeaderReportedApplier(topicState))
                .withEventApplier(
                    MetadataIntent.BROKER_REGISTERED, new BrokerRegisteredApplier(brokerState))
                .withEventApplier(
                    MetadataIntent.BROKER_FENCED, new BrokerFencedApplier(brokerState))
                .withEventApplier(
                    MetadataIntent.BROKER_DRAINING, new BrokerDrainingApplier(brokerState))
                .withEventApplier(
                    MetadataIntent.BROKER_DEREGISTERED, new BrokerDeregisteredApplier(brokerState))
                .withListener(
                    new BrokerEvictionTask(
                        BROKER_EVICTION_INTERVAL,
                        BROKER_SESSION_TIMEOUT,
                        taskBrokerState(),
                        brokerLiveness,
                        clock))
                .withListener(
                    new PlacementHealTask(
                        PLACEMENT_HEAL_INTERVAL, taskTopicState(), taskBrokerState())));
  }

  // Async tasks read state off the processing actor, so each gets its own private ZeebeDb context
  // (its flyweights belong to the task group), mirroring consumer-groups' taskGroupState().
  private DbTopicState taskTopicState() {
    return new DbTopicState(zeebeDb, zeebeDb.createContext());
  }

  private DbBrokerState taskBrokerState() {
    return new DbBrokerState(zeebeDb, zeebeDb.createContext());
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
            .setTarget(metadata.target())
            .setPassive(metadata.passive());
    writeTopicCommand(name, command, MetadataIntent.REGISTER_TOPIC);
  }

  /**
   * Client topic requests: the service-layer command is written as-is, and the processor validates
   * it against the replicated registry, resolves the placement, and replies after commit (the
   * future completes with the encoded response). Leader only.
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

  /**
   * Partition-leadership report from a topic partition's elected Raft leader: written as a command
   * that the {@code ReportPartitionLeaderProcessor} validates and records, replying after commit so
   * the reporter knows it is durable. Leader only.
   */
  public CompletableFuture<byte[]> reportPartitionLeader(final TopicRecord command) {
    return writeRequest(
        MetadataIntent.REPORT_PARTITION_LEADER, MetadataRecordValues.TOPIC_VALUE_TYPE, command);
  }

  /**
   * Broker registration: written as a command that the {@code RegisterBrokerProcessor} validates
   * and stamps with a fresh epoch, replying after commit (the future completes with the encoded
   * {@code RegisterBrokerResponse}). Leader only.
   */
  public CompletableFuture<byte[]> registerBroker(final BrokerRecord command) {
    return writeRequest(
        MetadataIntent.REGISTER_BROKER, MetadataRecordValues.BROKER_VALUE_TYPE, command);
  }

  /**
   * A fresh off-actor read view of the topic registry on this stream's {@link ZeebeDb}. Each reader
   * actor (the leader's manager, each broker's reconcile loop) takes its own so it reads committed
   * state without sharing the processor's flyweights.
   */
  public TopicQueryService newTopicQueryService() {
    return new TopicQueryService(zeebeDb);
  }

  /** A fresh off-actor read view of the broker registry — one per reader actor. */
  public BrokerQueryService newBrokerQueryService() {
    return new BrokerQueryService(zeebeDb);
  }

  /**
   * The shared, leader-local broker liveness mirror — published by the heartbeat handler and swept
   * by the {@code BrokerEvictionTask}. Created at stream startup so both share one instance.
   */
  public BrokerLivenessMirror brokerLiveness() {
    return brokerLiveness;
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

  /**
   * Marks a broker as draining for controlled shutdown (internal, fire-and-forget). Leader only.
   */
  public void drainBroker(final BrokerRecord command) {
    writeBrokerCommand(command, MetadataIntent.DRAIN_BROKER);
  }

  /** Removes a drained broker from the registry (internal, fire-and-forget). Leader only. */
  public void deregisterBroker(final BrokerRecord command) {
    writeBrokerCommand(command, MetadataIntent.DEREGISTER_BROKER);
  }

  private void writeBrokerCommand(final BrokerRecord command, final MetadataIntent intent) {
    if (writer == null) {
      return;
    }
    final var metadata =
        new RecordMetadata()
            .recordType(RecordType.COMMAND)
            .valueType(MetadataRecordValues.BROKER_VALUE_TYPE)
            .intent(intent);
    final var result =
        writer.tryWrite(WriteContext.internal(), LogAppendEntry.of(metadata, command));
    if (result.isLeft()) {
      LOG.warn(
          "Failed to write broker command {} for {}: {}",
          intent,
          command.getBrokerId(),
          result.getLeft());
    }
  }
}
