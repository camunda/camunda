/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.membership;

import static io.camunda.eventbridge.protocol.request.coordination.CoordinationErrorCode.INVALID_GROUP_ID;
import static io.camunda.eventbridge.protocol.request.coordination.CoordinationErrorCode.NONE;

import io.camunda.eventbridge.consumergroups.record.MembershipRecord;
import io.camunda.eventbridge.consumergroups.record.OffsetCommitRecord;
import io.camunda.eventbridge.consumergroups.session.MemberLivenessMirror;
import io.camunda.eventbridge.consumergroups.state.group.GroupSnapshot;
import io.camunda.eventbridge.consumergroups.stream.CoordinatorStream;
import io.camunda.eventbridge.protocol.request.coordination.CommitOffsetRequest;
import io.camunda.eventbridge.protocol.request.coordination.DescribeGroupsRequest;
import io.camunda.eventbridge.protocol.request.coordination.DescribeGroupsResponse;
import io.camunda.eventbridge.protocol.request.coordination.HeartbeatRequest;
import io.camunda.eventbridge.protocol.request.coordination.JoinGroupRequest;
import io.camunda.eventbridge.protocol.request.coordination.LeaveGroupRequest;
import io.camunda.eventbridge.protocol.request.coordination.OffsetFetchRequest;
import io.camunda.eventbridge.protocol.request.coordination.OffsetFetchResponse;
import io.camunda.eventbridge.protocol.transport.CoordinationResponseEncoder;
import io.camunda.eventbridge.stream.CommandRejectionException;
import io.camunda.zeebe.scheduler.Actor;
import java.time.InstantSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BiConsumer;
import org.agrona.concurrent.SnowflakeIdGenerator;

/**
 * The leader-side coordinator for consumer-group requests. Membership and target assignment are
 * first-class replicated state on the coordinator stream (KIP-848 model): {@code JOIN_GROUP} and
 * {@code LEAVE_GROUP} are written as commands and validated in their processors, so this manager
 * only turns a request into a command and bridges the committed reply; offset-fetch and describe
 * are served as reads off replicated state. Heartbeats — the one request that stays
 * request/response and runs the in-memory assign/revoke handshake — are delegated to the {@link
 * HeartbeatHandler}, which also keeps the {@link MemberLivenessMirror} the off-actor {@code
 * SessionEvictionTask} reads up to date.
 */
public class ConsumerGroupCoordinator extends Actor {

  private final int partitionId;
  private final CoordinatorStream coordinatorStream;
  private final MemberLivenessMirror liveness;
  private final HeartbeatHandler heartbeatHandler;
  private final SnowflakeIdGenerator idGenerator;

  public ConsumerGroupCoordinator(
      final int partitionId, final InstantSource clock, final CoordinatorStream coordinatorStream) {
    this.partitionId = partitionId;
    this.coordinatorStream = coordinatorStream;
    liveness = coordinatorStream.liveness();
    heartbeatHandler = new HeartbeatHandler(partitionId, clock, coordinatorStream, liveness);
    idGenerator = new SnowflakeIdGenerator(1L);
  }

  @Override
  public String getName() {
    return "ConsumerGroupCoordinator-" + partitionId;
  }

  @Override
  protected void onActorStarted() {
    // The durable membership/target was replayed into the stream's state before this manager
    // started, so seed the heartbeat handshake from it: re-attaching consumers get fresh deadlines
    // and are treated as already at their target (no rejoin storm after a failover). The eviction
    // sweep runs as a separate task on the stream's async group.
    heartbeatHandler.seed();
  }

  @Override
  protected void onActorClosing() {
    // Leadership is being given up — abandon the ephemeral liveness so the eviction task (also
    // stopping) cannot act on stale sessions; a new leader reseeds it from replicated state.
    liveness.clear();
  }

  /**
   * Writes a {@code JOIN_GROUP} command and bridges the committed {@code JoinGroupResponse}. The
   * command carries only the request's intent (group id, subscribed topic, instance id); the
   * processor resolves the topic's partition count from the registry at processing time and
   * validates it, so the decision is made by the leader that actually produces the durable event.
   */
  public CompletableFuture<byte[]> handleJoinGroup(final JoinGroupRequest request) {
    final var result = new CompletableFuture<byte[]>();
    actor.run(
        () -> {
          final var command =
              new MembershipRecord()
                  .setGroupId(request.getGroupId())
                  .setTopics(request.getTopics())
                  .setMemberId(generateMemberId())
                  .setInstanceId(request.getInstanceId());
          coordinatorStream.joinGroup(command).whenComplete(bridge(result));
        });
    return result;
  }

  /** Writes a {@code LEAVE_GROUP} command and bridges the committed {@code LeaveGroupResponse}. */
  public CompletableFuture<byte[]> handleLeaveGroup(final LeaveGroupRequest request) {
    final var result = new CompletableFuture<byte[]>();
    actor.run(
        () -> {
          final var command =
              new MembershipRecord()
                  .setGroupId(request.getGroupId())
                  .setMemberId(request.getMemberId())
                  .setMemberEpoch(request.getMemberEpoch());
          coordinatorStream.leaveGroup(command).whenComplete(bridge(result));
        });
    return result;
  }

  /**
   * Serves an offset fetch (request/response, no log write): reads the group's committed offsets
   * from state off the processing actor — the read-from-state path, not a command through the
   * stream — and returns the framed reply.
   */
  public CompletableFuture<byte[]> handleOffsetFetch(final OffsetFetchRequest request) {
    final var result = new CompletableFuture<byte[]>();
    actor.run(
        () -> {
          try {
            final var groupId = request.getGroupId();
            final var response = new OffsetFetchResponse();
            if (groupId == null || groupId.isEmpty()) {
              response.setErrorCode(INVALID_GROUP_ID);
            } else {
              response
                  .setErrorCode(NONE)
                  .setCommittedOffsets(coordinatorStream.committedOffsets(groupId));
            }
            result.complete(CoordinationResponseEncoder.encode(response));
          } catch (final RuntimeException e) {
            result.completeExceptionally(e);
          }
        });
    return result;
  }

  /**
   * Serves a describe-groups read (request/response, no log write): reads the replicated group
   * lifecycle/epochs/roster from state off the processing actor. An empty {@code groupId} returns
   * all groups on this shard; a set one narrows to that group (empty result if it lives on another
   * shard or does not exist).
   */
  public CompletableFuture<byte[]> handleDescribeGroups(final DescribeGroupsRequest request) {
    final var result = new CompletableFuture<byte[]>();
    actor.run(
        () -> {
          try {
            final var groupId = request.getGroupId();
            final var response = new DescribeGroupsResponse();
            final List<GroupSnapshot> groups;
            if (groupId == null || groupId.isEmpty()) {
              groups = coordinatorStream.groupSnapshots();
            } else {
              final var group = coordinatorStream.groupSnapshot(groupId);
              groups = group == null ? List.of() : List.of(group);
            }
            for (final var group : groups) {
              final var members = new LinkedHashMap<String, Long>();
              group.members().forEach((id, m) -> members.put(id, m.assignedEpoch()));
              response.addGroup(
                  description ->
                      description
                          .setGroupId(group.groupId())
                          .setState(group.state().name())
                          .setGroupEpoch(group.groupEpoch())
                          .setAssignmentEpoch(group.assignmentEpoch())
                          .setSubscriptions(group.subscriptions())
                          .setMembers(members));
            }
            result.complete(CoordinationResponseEncoder.encode(response));
          } catch (final RuntimeException e) {
            result.completeExceptionally(e);
          }
        });
    return result;
  }

  /** Serves a heartbeat (request/response, no log write) and returns the framed reply. */
  public CompletableFuture<byte[]> handleHeartbeat(final HeartbeatRequest request) {
    final var result = new CompletableFuture<byte[]>();
    actor.run(
        () -> {
          try {
            result.complete(CoordinationResponseEncoder.encode(heartbeatHandler.handle(request)));
          } catch (final RuntimeException e) {
            result.completeExceptionally(e);
          }
        });
    return result;
  }

  /**
   * Replicates an offset commit through the coordinator stream and returns the encoded {@code
   * CommitOffsetResponse}. Validation (member epoch + partition ownership) happens in the {@code
   * OffsetCommitProcessor} against replicated membership, not here.
   */
  public CompletableFuture<byte[]> handleCommit(final CommitOffsetRequest request) {
    final var command =
        new OffsetCommitRecord()
            .setGroupId(request.getGroupId())
            .setTopic(request.getTopic())
            .setMemberId(request.getMemberId())
            .setMemberEpoch(request.getMemberEpoch())
            .setPartitionId(request.getPartitionId())
            .setOffset(request.getPosition());

    final var result = new CompletableFuture<byte[]>();
    actor.run(() -> coordinatorStream.commit(command).whenComplete(bridge(result)));
    return result;
  }

  /**
   * Completes {@code result} with the committed stream reply, framed in the {@code
   * ExecuteCoordinateResponse} envelope the gateway's broker client decodes — so every handler
   * method returns ready-to-send bytes and the transport layer only routes.
   *
   * <p>A rejected command (the processor wrote a {@code COMMAND_REJECTION} reply, surfaced here as
   * a {@link CommandRejectionException}) is framed as a <em>rejection</em> response, not completed
   * exceptionally: the gateway decodes it into a {@code BrokerRejection} and maps it to an HTTP
   * status. A genuine transport/processing failure still completes exceptionally.
   */
  private BiConsumer<byte[], Throwable> bridge(final CompletableFuture<byte[]> result) {
    return (response, error) -> {
      if (error == null) {
        result.complete(CoordinationResponseEncoder.encodeValue(response));
        return;
      }
      final var cause =
          error instanceof CompletionException && error.getCause() != null
              ? error.getCause()
              : error;
      if (cause instanceof final CommandRejectionException rejection) {
        result.complete(
            CoordinationResponseEncoder.encodeRejection(rejection.type(), rejection.getMessage()));
      } else {
        result.completeExceptionally(error);
      }
    };
  }

  private String generateMemberId() {
    return String.valueOf(idGenerator.nextId());
  }
}
