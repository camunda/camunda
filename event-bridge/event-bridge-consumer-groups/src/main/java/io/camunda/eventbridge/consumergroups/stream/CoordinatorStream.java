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
import io.camunda.eventbridge.consumergroups.processing.CoordinationValidator;
import io.camunda.eventbridge.consumergroups.processing.DeleteGroupProcessor;
import io.camunda.eventbridge.consumergroups.processing.GroupRetentionTask;
import io.camunda.eventbridge.consumergroups.processing.JoinGroupProcessor;
import io.camunda.eventbridge.consumergroups.processing.LeaveGroupProcessor;
import io.camunda.eventbridge.consumergroups.processing.OffsetCommitProcessor;
import io.camunda.eventbridge.consumergroups.processing.RebalanceAssignorTask;
import io.camunda.eventbridge.consumergroups.processing.RebalanceProcessor;
import io.camunda.eventbridge.consumergroups.processing.ReconcileMemberProcessor;
import io.camunda.eventbridge.consumergroups.processing.SessionEvictionTask;
import io.camunda.eventbridge.consumergroups.processing.TransitionValidator;
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
import io.camunda.eventbridge.consumergroups.state.immutable.ConsumerGroupState;
import io.camunda.eventbridge.consumergroups.state.offset.DbOffsetState;
import io.camunda.eventbridge.consumergroups.state.offset.OffsetQueryService;
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

  /** How often the async assignor scans for groups whose (debounced) rebalance is due. */
  private static final Duration ASSIGNOR_INTERVAL = Duration.ofSeconds(1);

  /**
   * How long a group's rebalance is debounced after it enters {@code PREPARING_REBALANCE}: a fixed
   * window from the first change (membership processors stamp {@code now + this} into state), so a
   * burst of joins/leaves yields one rebalance rather than one per change.
   */
  private static final Duration REBALANCE_DEBOUNCE = Duration.ofSeconds(2);

  // Session-eviction sweep cadence and the liveness windows it enforces: the heartbeat session
  // timeout, and the longest a rebalance may stall before non-converged members are evicted.
  private static final Duration EVICTION_INTERVAL = Duration.ofSeconds(1);
  private static final Duration SESSION_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration REBALANCE_TIMEOUT = Duration.ofSeconds(30);

  // How long an EMPTY group (and its committed offsets) is retained before being reclaimed. The
  // retention deadline is stamped into replicated state, so the scan only needs to run coarsely:
  // its cadence bounds how promptly an
  // expired group is reclaimed (worst case retention + interval), never the deadline itself.
  private static final Duration EMPTY_GROUP_RETENTION = Duration.ofMinutes(5);
  private static final Duration RETENTION_INTERVAL = Duration.ofMinutes(1);

  private final InstantSource clock;
  private final TopicRegistry topicRegistry;
  private DbOffsetState offsetState;
  private DbConsumerGroupState groupState;
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
    // The processing-actor state views (read+written by the processors/appliers). Off-actor reads
    // are served by the reader actors' own query services (see newGroupQueryService /
    // newOffsetQueryService), not from here, since group membership and offsets are unbounded.
    offsetState = new DbOffsetState(zeebeDb, zeebeDb.createContext());
    groupState = new DbConsumerGroupState(zeebeDb, zeebeDb.createContext());
    liveness = new MemberLivenessMirror();
  }

  @Override
  protected RecordProcessor createRecordProcessor() {
    // Client-command validation (with error codes + replies) and internal state-machine transition
    // guards (reason-only, no reply) — both read the replicated state on the processing actor.
    final var validator = new CoordinationValidator(groupState, topicRegistry);
    final var transitions = new TransitionValidator(groupState);
    return new RecordProcessingEngine(
        processors ->
            processors
                .onCommand(
                    EventBridgeRecordValues.OFFSET_VALUE_TYPE,
                    CoordinatorIntent.COMMIT_OFFSET,
                    new OffsetCommitProcessor(processors.writers(), offsetState, validator))
                .onCommand(
                    EventBridgeRecordValues.MEMBERSHIP_VALUE_TYPE,
                    CoordinatorIntent.JOIN_GROUP,
                    new JoinGroupProcessor(
                        processors.writers(), groupState, validator, REBALANCE_DEBOUNCE))
                .onCommand(
                    EventBridgeRecordValues.MEMBERSHIP_VALUE_TYPE,
                    CoordinatorIntent.LEAVE_GROUP,
                    new LeaveGroupProcessor(
                        processors.writers(), groupState, validator, REBALANCE_DEBOUNCE))
                .onCommand(
                    EventBridgeRecordValues.REBALANCE_VALUE_TYPE,
                    CoordinatorIntent.REBALANCE_GROUP,
                    new RebalanceProcessor(processors.writers(), transitions))
                .onCommand(
                    EventBridgeRecordValues.MEMBERSHIP_VALUE_TYPE,
                    CoordinatorIntent.RECONCILE_MEMBER,
                    new ReconcileMemberProcessor(processors.writers(), groupState, transitions))
                .onCommand(
                    EventBridgeRecordValues.MEMBERSHIP_VALUE_TYPE,
                    CoordinatorIntent.DELETE_GROUP,
                    new DeleteGroupProcessor(processors.writers(), transitions))
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
                        ASSIGNOR_INTERVAL, taskGroupState(), new BalancedStickyAssignor(), clock))
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
   * A group-query view on its <em>own</em> {@link io.camunda.zeebe.db.ZeebeDb} context, for a
   * reader actor (the {@code HeartbeatHandler}, the {@code ConsumerGroupQueryHandler}) so each
   * reads off-actor without sharing flyweights. One instance per reader actor.
   */
  public ConsumerGroupQueryService newGroupQueryService() {
    return new ConsumerGroupQueryService(zeebeDb);
  }

  /** An offset-query view on its own context, for a reader actor other than the coordinator. */
  public OffsetQueryService newOffsetQueryService() {
    return new OffsetQueryService(zeebeDb);
  }
}
