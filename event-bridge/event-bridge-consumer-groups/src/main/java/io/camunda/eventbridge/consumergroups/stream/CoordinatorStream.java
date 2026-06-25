/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.stream;

import io.camunda.eventbridge.consumergroups.assignor.BalancedStickyAssignor;
import io.camunda.eventbridge.consumergroups.membership.TopicRegistry;
import io.camunda.eventbridge.consumergroups.processing.CoordinationChecks;
import io.camunda.eventbridge.consumergroups.processing.DeleteGroupProcessor;
import io.camunda.eventbridge.consumergroups.processing.GroupRetentionTask;
import io.camunda.eventbridge.consumergroups.processing.JoinGroupProcessor;
import io.camunda.eventbridge.consumergroups.processing.LeaveGroupProcessor;
import io.camunda.eventbridge.consumergroups.processing.OffsetCommitProcessor;
import io.camunda.eventbridge.consumergroups.processing.RebalanceAssignorTask;
import io.camunda.eventbridge.consumergroups.processing.RebalanceProcessor;
import io.camunda.eventbridge.consumergroups.processing.ReconcileMemberProcessor;
import io.camunda.eventbridge.consumergroups.processing.SessionEvictionTask;
import io.camunda.eventbridge.consumergroups.record.EventBridgeRecordValues;
import io.camunda.eventbridge.consumergroups.record.MembershipRecord;
import io.camunda.eventbridge.consumergroups.record.OffsetCommitRecord;
import io.camunda.eventbridge.consumergroups.session.MemberLivenessMirror;
import io.camunda.eventbridge.consumergroups.state.EventBridgeColumnFamilies;
import io.camunda.eventbridge.consumergroups.state.appliers.GroupDeletedApplier;
import io.camunda.eventbridge.consumergroups.state.appliers.GroupRebalancedApplier;
import io.camunda.eventbridge.consumergroups.state.appliers.MemberJoinedApplier;
import io.camunda.eventbridge.consumergroups.state.appliers.MemberLeftApplier;
import io.camunda.eventbridge.consumergroups.state.appliers.MemberReconciledApplier;
import io.camunda.eventbridge.consumergroups.state.appliers.OffsetCommittedApplier;
import io.camunda.eventbridge.consumergroups.state.group.ConsumerGroupQueryService;
import io.camunda.eventbridge.consumergroups.state.group.DbConsumerGroupState;
import io.camunda.eventbridge.consumergroups.state.group.GroupSnapshot;
import io.camunda.eventbridge.consumergroups.state.immutable.ConsumerGroupState;
import io.camunda.eventbridge.consumergroups.state.offset.DbOffsetState;
import io.camunda.eventbridge.consumergroups.state.offset.OffsetQueryService;
import io.camunda.eventbridge.protocol.topic.TopicPartition;
import io.camunda.eventbridge.stream.RecordProcessingEngine;
import io.camunda.eventbridge.stream.ReplicatedStream;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.logstreams.storage.LogStorage;
import io.camunda.zeebe.protocol.record.intent.CoordinatorIntent;
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

/**
 * The coordinator's replicated state engine: a {@link ReplicatedStream} over the coordinator Raft
 * partition's log. Consumer-group membership, target assignment, and committed offsets are first-
 * class replicated state — written as commands, validated + applied by the registered processors
 * and appliers, and re-emitted as events that followers replay into the same {@link
 * DbConsumerGroupState} / {@link DbOffsetState}. The async {@link RebalanceAssignorTask} runs off
 * the processing path (leader only) and proposes target assignments.
 */
public final class CoordinatorStream extends ReplicatedStream<EventBridgeColumnFamilies> {

  /**
   * How often the async assignor scans for groups needing a rebalance (also the debounce window).
   */
  private static final Duration ASSIGNOR_INTERVAL = Duration.ofSeconds(1);

  // Session-eviction sweep cadence and the liveness windows it enforces: the heartbeat session
  // timeout, and the longest a rebalance may stall before non-converged members are evicted.
  private static final Duration EVICTION_INTERVAL = Duration.ofSeconds(1);
  private static final Duration SESSION_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration REBALANCE_TIMEOUT = Duration.ofSeconds(30);

  // How long an EMPTY group (and its committed offsets) is retained before being reclaimed — the
  // event-bridge analog of Kafka's offsets.retention. The retention deadline is stamped into
  // replicated state, so the scan only needs to run coarsely: its cadence bounds how promptly an
  // expired group is reclaimed (worst case retention + interval), never the deadline itself.
  private static final Duration EMPTY_GROUP_RETENTION = Duration.ofMinutes(5);
  private static final Duration RETENTION_INTERVAL = Duration.ofMinutes(1);

  private final InstantSource clock;
  private final TopicRegistry topicRegistry;
  private DbOffsetState offsetState;
  private OffsetQueryService offsetQuery;
  private DbConsumerGroupState groupState;
  // Off-actor group reads for the coordinator actor (heartbeat/describe/seed) — its own context.
  private ConsumerGroupQueryService coordinatorGroupQuery;
  private MemberLivenessMirror liveness;

  /**
   * @param zeebeDb the state DB, recovered/owned by the {@link
   *     io.camunda.zeebe.broker.system.partitions.StateController} (so snapshots can manage it)
   * @param topicRegistry the live topic-registry view used by the join processor to resolve and
   *     validate a subscribed topic's partition count at processing time
   */
  public CoordinatorStream(
      final int partitionId,
      final LogStorage logStorage,
      final ActorSchedulingService actorScheduler,
      final ZeebeDb<EventBridgeColumnFamilies> zeebeDb,
      final InstantSource clock,
      final MeterRegistry meterRegistry,
      final TopicRegistry topicRegistry) {
    super(partitionId, logStorage, actorScheduler, zeebeDb, clock, meterRegistry);
    this.clock = clock;
    this.topicRegistry = topicRegistry;
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
    // Offsets are unbounded, so they are read on demand from state (its own context), not mirrored.
    offsetQuery = new OffsetQueryService(zeebeDb);
    groupState = new DbConsumerGroupState(zeebeDb, zeebeDb.createContext());
    // Group membership is unbounded, so off-actor reads (heartbeat/describe/seed) go to state via a
    // query service (its own context), not an in-memory mirror.
    coordinatorGroupQuery = new ConsumerGroupQueryService(zeebeDb);
    liveness = new MemberLivenessMirror();
  }

  @Override
  protected RecordProcessor createRecordProcessor() {
    final var checks = new CoordinationChecks(groupState, topicRegistry);
    return new RecordProcessingEngine(
        processors ->
            processors
                .onCommand(
                    EventBridgeRecordValues.OFFSET_VALUE_TYPE,
                    CoordinatorIntent.COMMIT_OFFSET,
                    new OffsetCommitProcessor(processors.writers(), offsetState, checks))
                .onCommand(
                    EventBridgeRecordValues.MEMBERSHIP_VALUE_TYPE,
                    CoordinatorIntent.JOIN_GROUP,
                    new JoinGroupProcessor(processors.writers(), groupState, checks))
                .onCommand(
                    EventBridgeRecordValues.MEMBERSHIP_VALUE_TYPE,
                    CoordinatorIntent.LEAVE_GROUP,
                    new LeaveGroupProcessor(processors.writers(), groupState, checks))
                .onCommand(
                    EventBridgeRecordValues.REBALANCE_VALUE_TYPE,
                    CoordinatorIntent.REBALANCE_GROUP,
                    new RebalanceProcessor(processors.writers(), groupState))
                .onCommand(
                    EventBridgeRecordValues.MEMBERSHIP_VALUE_TYPE,
                    CoordinatorIntent.RECONCILE_MEMBER,
                    new ReconcileMemberProcessor(processors.writers(), groupState))
                .onCommand(
                    EventBridgeRecordValues.MEMBERSHIP_VALUE_TYPE,
                    CoordinatorIntent.DELETE_GROUP,
                    new DeleteGroupProcessor(processors.writers(), groupState))
                .withEventApplier(
                    CoordinatorIntent.OFFSET_COMMITTED, new OffsetCommittedApplier(offsetState))
                .withEventApplier(
                    CoordinatorIntent.MEMBER_JOINED, new MemberJoinedApplier(groupState))
                .withEventApplier(CoordinatorIntent.MEMBER_LEFT, new MemberLeftApplier(groupState))
                .withEventApplier(
                    CoordinatorIntent.MEMBER_RECONCILED, new MemberReconciledApplier(groupState))
                .withEventApplier(
                    CoordinatorIntent.GROUP_REBALANCED, new GroupRebalancedApplier(groupState))
                .withEventApplier(
                    CoordinatorIntent.GROUP_DELETED,
                    new GroupDeletedApplier(groupState, offsetState))
                .withListener(
                    new RebalanceAssignorTask(
                        ASSIGNOR_INTERVAL, taskGroupState(), new BalancedStickyAssignor()))
                .withListener(
                    new GroupRetentionTask(
                        RETENTION_INTERVAL, EMPTY_GROUP_RETENTION, taskGroupState(), clock))
                .withListener(
                    new SessionEvictionTask(
                        EVICTION_INTERVAL,
                        SESSION_TIMEOUT,
                        REBALANCE_TIMEOUT,
                        taskGroupState(),
                        liveness,
                        clock)));
  }

  /**
   * A read-only consumer-group state on its own {@link ZeebeDb} context, for one async task to read
   * off the processing actor — the event-bridge counterpart of the engine handing each scheduled
   * task its own {@code ScheduledTaskState}. A fresh instance (and context) per task keeps the
   * flyweights confined to that task's actor.
   */
  private ConsumerGroupState taskGroupState() {
    return new DbConsumerGroupState(zeebeDb, zeebeDb.createContext());
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
   * Writes a {@code JOIN_GROUP} command; the returned future completes with the encoded {@code
   * JoinGroupResponse} after the command commits (the {@link JoinGroupProcessor} assigns/echoes the
   * member id + epoch and stages the reply). Leader only.
   */
  public CompletableFuture<byte[]> joinGroup(final MembershipRecord command) {
    return writeRequest(
        CoordinatorIntent.JOIN_GROUP, EventBridgeRecordValues.MEMBERSHIP_VALUE_TYPE, command);
  }

  /**
   * Writes a {@code LEAVE_GROUP} command; the returned future completes with the encoded {@code
   * LeaveGroupResponse} after the command commits. Eviction of timed-out members goes through the
   * {@link SessionEvictionTask} instead, which appends {@code LEAVE_GROUP} on the processing path.
   * Leader only.
   */
  public CompletableFuture<byte[]> leaveGroup(final MembershipRecord command) {
    return writeRequest(
        CoordinatorIntent.LEAVE_GROUP, EventBridgeRecordValues.MEMBERSHIP_VALUE_TYPE, command);
  }

  /**
   * Fire-and-forget: records that a member has reconciled to the current target (the coordinator
   * appends this from a heartbeat once the member owns exactly its target). No reply — the effect
   * (STABLE once all members converge) is observed via replicated state. Leader only.
   */
  public void reconcileMember(final MembershipRecord command) {
    writeCommand(
        CoordinatorIntent.RECONCILE_MEMBER, EventBridgeRecordValues.MEMBERSHIP_VALUE_TYPE, command);
  }

  /**
   * The shared ephemeral member-liveness mirror: the coordinator's heartbeat handler publishes per
   * group, the off-actor {@link SessionEvictionTask} reads it to expire dead sessions. Created at
   * startup so both — the task (built with the record processor) and the coordinator (built on
   * leader activation) — share one instance.
   */
  public MemberLivenessMirror liveness() {
    return liveness;
  }

  /**
   * A snapshot of a group's membership/assignment read from state (off the processing actor via the
   * coordinator's query context), or {@code null} if the group does not exist.
   */
  public GroupSnapshot groupSnapshot(final String groupId) {
    return coordinatorGroupQuery.groupSnapshot(groupId);
  }

  /** Snapshots of all groups read from state — for the coordinator's describe/seed reads. */
  public List<GroupSnapshot> groupSnapshots() {
    return coordinatorGroupQuery.allGroups();
  }

  /**
   * Committed offsets for a group ({@code (topic, partition) → position}), read from state via the
   * {@link OffsetQueryService} (off the processing actor, no in-memory mirror).
   */
  public Map<TopicPartition, Long> committedOffsets(final String groupId) {
    return offsetQuery.committedOffsets(groupId);
  }
}
