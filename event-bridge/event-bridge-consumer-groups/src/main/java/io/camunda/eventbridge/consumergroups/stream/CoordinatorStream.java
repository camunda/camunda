/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.stream;

import io.camunda.eventbridge.consumergroups.assignor.BalancedStickyAssignor;
import io.camunda.eventbridge.consumergroups.processing.CoordinationChecks;
import io.camunda.eventbridge.consumergroups.processing.JoinGroupProcessor;
import io.camunda.eventbridge.consumergroups.processing.LeaveGroupProcessor;
import io.camunda.eventbridge.consumergroups.processing.OffsetCommitProcessor;
import io.camunda.eventbridge.consumergroups.processing.RebalanceAssignorTask;
import io.camunda.eventbridge.consumergroups.processing.RebalanceProcessor;
import io.camunda.eventbridge.consumergroups.record.CoordinatorIntent;
import io.camunda.eventbridge.consumergroups.record.EventBridgeRecordValues;
import io.camunda.eventbridge.consumergroups.record.MembershipRecord;
import io.camunda.eventbridge.consumergroups.record.OffsetCommitRecord;
import io.camunda.eventbridge.consumergroups.state.EventBridgeColumnFamilies;
import io.camunda.eventbridge.consumergroups.state.appliers.GroupRebalancedApplier;
import io.camunda.eventbridge.consumergroups.state.appliers.MemberJoinedApplier;
import io.camunda.eventbridge.consumergroups.state.appliers.MemberLeftApplier;
import io.camunda.eventbridge.consumergroups.state.appliers.OffsetCommittedApplier;
import io.camunda.eventbridge.consumergroups.state.group.DbConsumerGroupState;
import io.camunda.eventbridge.consumergroups.state.group.GroupSnapshot;
import io.camunda.eventbridge.consumergroups.state.offset.DbOffsetState;
import io.camunda.eventbridge.stream.RecordProcessingEngine;
import io.camunda.eventbridge.stream.ReplicatedStream;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.logstreams.storage.LogStorage;
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

  private DbOffsetState offsetState;
  private DbConsumerGroupState groupState;

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
    groupState = new DbConsumerGroupState(zeebeDb, zeebeDb.createContext());
    // Seed the thread-safe mirrors from durable state before the processor starts (no concurrent
    // access yet), so a replica recovered from a snapshot exposes its state even before any replay.
    offsetState.seedMirror();
    groupState.seedMirror();
  }

  @Override
  protected RecordProcessor createRecordProcessor() {
    final var checks = new CoordinationChecks(groupState);
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
                .withEventApplier(
                    CoordinatorIntent.OFFSET_COMMITTED, new OffsetCommittedApplier(offsetState))
                .withEventApplier(
                    CoordinatorIntent.MEMBER_JOINED, new MemberJoinedApplier(groupState))
                .withEventApplier(CoordinatorIntent.MEMBER_LEFT, new MemberLeftApplier(groupState))
                .withEventApplier(
                    CoordinatorIntent.GROUP_REBALANCED, new GroupRebalancedApplier(groupState))
                .withListener(
                    new RebalanceAssignorTask(
                        ASSIGNOR_INTERVAL, groupState, new BalancedStickyAssignor())));
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
   * LeaveGroupResponse} after the command commits. Also used (future ignored) when the coordinator
   * evicts a timed-out member. Leader only.
   */
  public CompletableFuture<byte[]> leaveGroup(final MembershipRecord command) {
    return writeRequest(
        CoordinatorIntent.LEAVE_GROUP, EventBridgeRecordValues.MEMBERSHIP_VALUE_TYPE, command);
  }

  /** A thread-safe snapshot of a group's replicated membership/assignment, or {@code null}. */
  public GroupSnapshot groupSnapshot(final String groupId) {
    return groupState.groupSnapshot(groupId);
  }

  /** Thread-safe snapshots of all groups (e.g. for the eviction scan). */
  public List<GroupSnapshot> groupSnapshots() {
    return groupState.groupSnapshots();
  }

  /** Committed offsets for a group ({@code partitionId → position}), read from the mirror. */
  public Map<Integer, Long> committedOffsets(final String groupId) {
    return offsetState.offsetsSnapshot(groupId);
  }
}
