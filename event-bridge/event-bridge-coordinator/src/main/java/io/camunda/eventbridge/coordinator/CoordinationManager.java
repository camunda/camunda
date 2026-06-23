/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.coordinator;

import static io.camunda.eventbridge.protocol.request.coordination.CoordinationErrorCode.NONE;
import static io.camunda.eventbridge.protocol.request.coordination.CoordinationErrorCode.REBALANCE_IN_PROGRESS;

import io.camunda.eventbridge.coordinator.assignor.PartitionAssignment.ReconciliationResult;
import io.camunda.eventbridge.coordinator.stream.CoordinatorStream;
import io.camunda.eventbridge.coordinator.stream.GroupMetadataCodec;
import io.camunda.eventbridge.coordinator.stream.TopicAssignmentGossip;
import io.camunda.eventbridge.coordinator.stream.TopicAssignments;
import io.camunda.eventbridge.coordinator.stream.TopicMetadata;
import io.camunda.eventbridge.core.coordinator.CoordinatorRouting;
import io.camunda.eventbridge.protocol.request.coordination.CommitOffsetRequest;
import io.camunda.eventbridge.protocol.request.coordination.CommitOffsetResponse;
import io.camunda.eventbridge.protocol.request.coordination.CoordinationErrorCode;
import io.camunda.eventbridge.protocol.request.coordination.CreateTopicRequest;
import io.camunda.eventbridge.protocol.request.coordination.CreateTopicResponse;
import io.camunda.eventbridge.protocol.request.coordination.DeleteTopicRequest;
import io.camunda.eventbridge.protocol.request.coordination.DeleteTopicResponse;
import io.camunda.eventbridge.protocol.request.coordination.HeartbeatRequest;
import io.camunda.eventbridge.protocol.request.coordination.HeartbeatResponse;
import io.camunda.eventbridge.protocol.request.coordination.JoinGroupRequest;
import io.camunda.eventbridge.protocol.request.coordination.JoinGroupResponse;
import io.camunda.eventbridge.protocol.request.coordination.LeaveGroupRequest;
import io.camunda.eventbridge.protocol.request.coordination.LeaveGroupResponse;
import io.camunda.eventbridge.protocol.request.coordination.ListTopicsRequest;
import io.camunda.eventbridge.protocol.request.coordination.ListTopicsResponse;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.util.Either;
import java.time.Duration;
import java.time.InstantSource;
import java.util.HashMap;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.agrona.concurrent.SnowflakeIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CoordinationManager extends Actor {

  private static final Logger LOG = LoggerFactory.getLogger(CoordinationManager.class);
  private static final Pattern TOPIC_NAME = Pattern.compile("[a-zA-Z0-9._-]{1,249}");

  private final int partitionId;
  private final ConsumerGroupRegistry registry;
  private final CoordinationValidator validator;
  private final int partitionCount;
  private final int clusterSize;
  private final SnowflakeIdGenerator idGenerator;
  private final InstantSource clock;

  // Committed offsets are now replicated state on the coordinator partition's stream processor
  // (RocksDB + Raft log), replacing the previous local-file OffsetStore. This survives clean
  // failover: a new leader resumes from the replayed state.
  private final CoordinatorStream coordinatorStream;

  private final Duration heartbeatTimeout;
  private final Duration heartbeatCheckInterval;

  // Anti-entropy broadcast of the topic registry to brokers (registry-shard leader only). Null on
  // non-registry shards and when no transport is wired (e.g. unit tests).
  private static final Duration TOPIC_BROADCAST_INTERVAL = Duration.ofSeconds(2);
  private final TopicAssignmentGossip.Publisher topicAssignmentPublisher;

  public CoordinationManager(
      final int partitionId,
      final int partitionCount,
      final int clusterSize,
      final InstantSource clock,
      final CoordinatorStream coordinatorStream,
      final TopicAssignmentGossip.Publisher topicAssignmentPublisher) {
    this.partitionId = partitionId;
    this.partitionCount = partitionCount;
    this.clusterSize = clusterSize;
    this.clock = clock;
    this.coordinatorStream = coordinatorStream;
    this.topicAssignmentPublisher = topicAssignmentPublisher;
    registry = new ConsumerGroupRegistry(new HashMap<>());
    validator = new CoordinationValidator(registry);
    idGenerator = new SnowflakeIdGenerator(1L);
    heartbeatTimeout = Duration.ofSeconds(10);
    heartbeatCheckInterval = Duration.ofSeconds(1);
  }

  protected void scheduleConsumerEviction() {
    schedule(heartbeatCheckInterval, this::evictSessions);
  }

  protected void evictSessions() {
    final var now = clock.instant();
    final var deadline = now.minus(heartbeatTimeout);

    registry
        .getAllGroups()
        .forEach(
            group -> {
              final var expired = group.getExpiredSessions(deadline);
              evictSessions(group, expired);

              if (group.isRebalanceTimedOut(now)) {
                final var nonConvergedSessions = group.getNonConvergedSessions();
                evictSessions(group, nonConvergedSessions);
              }
            });

    scheduleConsumerEviction();
  }

  private void evictSessions(final ConsumerGroup group, final List<GroupMemberSession> sessions) {
    sessions.forEach(
        session -> {
          final var metadata = session.getMetadata();
          group.removeSession(metadata.getMemberId());
          if (metadata.isStaticMember()) {
            metadata.incrementMemberEpoch();
          }
        });
  }

  public ActorFuture<JoinGroupResponse> handleJoinGroup(final JoinGroupRequest request) {
    return actor.call(() -> joinGroup(request));
  }

  public ActorFuture<LeaveGroupResponse> handleLeaveGroup(final LeaveGroupRequest request) {
    return actor.call(() -> leaveGroup(request));
  }

  public ActorFuture<HeartbeatResponse> handleHeartbeat(final HeartbeatRequest request) {
    return actor.call(() -> heartbeat(request));
  }

  public ActorFuture<CommitOffsetResponse> handleCommit(final CommitOffsetRequest request) {
    final CompletableActorFuture<CommitOffsetResponse> result = new CompletableActorFuture<>();
    actor.run(() -> commit(request, result));
    return result;
  }

  public ActorFuture<CreateTopicResponse> handleCreateTopic(final CreateTopicRequest request) {
    return actor.call(() -> createTopic(request));
  }

  public ActorFuture<DeleteTopicResponse> handleDeleteTopic(final DeleteTopicRequest request) {
    return actor.call(() -> deleteTopic(request));
  }

  public ActorFuture<ListTopicsResponse> handleListTopics(final ListTopicsRequest request) {
    return actor.call(this::listTopics);
  }

  private CreateTopicResponse createTopic(final CreateTopicRequest request) {
    final var name = request.getName();
    if (name == null || !TOPIC_NAME.matcher(name).matches()) {
      return new CreateTopicResponse().setErrorCode(CoordinationErrorCode.INVALID_TOPIC);
    }
    if (request.getPartitionCount() < 1 || request.getReplicationFactor() < 1) {
      return new CreateTopicResponse().setErrorCode(CoordinationErrorCode.INVALID_TOPIC);
    }
    if (coordinatorStream.topicsSnapshot().containsKey(name)) {
      return new CreateTopicResponse().setErrorCode(CoordinationErrorCode.TOPIC_ALREADY_EXISTS);
    }
    // Decide placement centrally and store it as data, so brokers obey it (rather than deriving it)
    // and a future rebalance can rewrite it. Register the desired state as CREATING; provisioning
    // and the advance to ACTIVE follow (later increment). The write is replicated through the
    // stream.
    final var assignment =
        TopicAssignments.roundRobin(
            request.getPartitionCount(), request.getReplicationFactor(), clusterSize);
    coordinatorStream.registerTopic(
        name,
        request.getPartitionCount(),
        request.getReplicationFactor(),
        TopicMetadata.TopicStatus.CREATING,
        assignment);
    return new CreateTopicResponse().setErrorCode(NONE);
  }

  private DeleteTopicResponse deleteTopic(final DeleteTopicRequest request) {
    final var name = request.getName();
    if (!coordinatorStream.topicsSnapshot().containsKey(name)) {
      return new DeleteTopicResponse().setErrorCode(CoordinationErrorCode.TOPIC_NOT_FOUND);
    }
    coordinatorStream.deleteTopic(name);
    return new DeleteTopicResponse().setErrorCode(NONE);
  }

  private ListTopicsResponse listTopics() {
    final var sb = new StringBuilder();
    coordinatorStream
        .topicsSnapshot()
        .forEach(
            (name, meta) ->
                sb.append(name)
                    .append(';')
                    .append(meta.partitionCount())
                    .append(';')
                    .append(meta.replicationFactor())
                    .append(';')
                    .append(meta.status().name())
                    .append(';')
                    .append(meta.encodedAssignment())
                    .append('\n'));
    return new ListTopicsResponse().setErrorCode(NONE).setPayload(sb.toString());
  }

  private void commit(
      final CommitOffsetRequest request,
      final CompletableActorFuture<CommitOffsetResponse> result) {
    final var groupId = request.getGroupId();
    final var memberId = request.getMemberId();

    final var validation =
        validator
            .isGroupIdValid(groupId)
            .flatMap(ok -> validator.isActiveMember(groupId, memberId))
            // Fence zombie commits: reject if the committing member's epoch is stale (a newer
            // generation has taken over), so a consumer that lost the partition cannot rewind it.
            .flatMap(
                ok ->
                    validator.isValidMemberEpoch(
                        request.getMemberEpoch(),
                        registry
                            .getGroup(groupId)
                            .getSession(memberId)
                            .getMetadata()
                            .getMemberEpoch()))
            // Fence by ownership: a member may only commit partitions the coordinator assigned it.
            .flatMap(
                ok ->
                    validator.ownsPartition(
                        registry.getGroup(groupId), memberId, request.getPartitionId()));

    if (validation.isLeft()) {
      result.complete(new CommitOffsetResponse().setErrorCode(validation.getLeft()));
      return;
    }

    // Replicate the commit through the coordinator stream; complete once it has been processed.
    coordinatorStream
        .commit(groupId, request.getPartitionId(), request.getPosition())
        .whenComplete(
            (committed, error) ->
                actor.run(
                    () -> {
                      if (error != null) {
                        result.complete(
                            new CommitOffsetResponse().setErrorCode(CoordinationErrorCode.UNKNOWN));
                      } else {
                        result.complete(
                            new CommitOffsetResponse()
                                .setErrorCode(NONE)
                                .setCommittedPosition(committed));
                      }
                    }));
  }

  private JoinGroupResponse joinGroup(final JoinGroupRequest request) {
    final var groupId = request.getGroupId();
    final var instanceId = request.getInstanceId();

    return validator
        .isGroupIdValid(groupId)
        .flatMap(ok -> registerMember(groupId, instanceId))
        .fold(
            errorCode -> new JoinGroupResponse().setErrorCode(errorCode),
            member ->
                new JoinGroupResponse()
                    .setErrorCode(REBALANCE_IN_PROGRESS)
                    .setMemberId(member.getMemberId())
                    .setMemberEpoch(member.getMemberEpoch()));
  }

  private Either<CoordinationErrorCode, MemberMetadata> registerMember(
      final String groupId, final String instanceId) {
    final var group = getOrCreateConsumerGroup(groupId);
    final var member = getOrCreateMemberMetadata(group, instanceId);

    // A static member re-joining while its session is still active is a duplicate/retried join
    // (at-least-once request delivery, or a client rejoin racing a still-live session). Answer it
    // idempotently with the current member id and epoch: do NOT bump the epoch or add a second
    // session. Bumping the epoch here without the live member learning the new value is exactly
    // what fenced its in-flight heartbeats and drove the perpetual rejoin storm.
    if (group.isActiveConsumer(member.getMemberId())) {
      return Either.right(member);
    }

    member.incrementMemberEpoch();
    group.addSession(new GroupMemberSession(member, clock.instant()));
    return Either.right(member);
  }

  private LeaveGroupResponse leaveGroup(final LeaveGroupRequest request) {
    final var groupId = request.getGroupId();
    final var memberId = request.getMemberId();
    final var memberEpoch = request.getMemberEpoch();

    return validator
        .isGroupIdValid(groupId)
        .flatMap(ok -> validator.isActiveMember(groupId, memberId))
        .flatMap(ok -> unregisterMember(groupId, memberId, memberEpoch))
        .fold(
            errorCode -> new LeaveGroupResponse().setErrorCode(errorCode),
            ignore -> new LeaveGroupResponse().setErrorCode(NONE));
  }

  private Either<CoordinationErrorCode, MemberMetadata> unregisterMember(
      final String groupId, final String memberId, final long memberEpoch) {
    final var group = registry.getGroup(groupId);
    final var session = group.getSession(memberId);
    final var metadata = session.getMetadata();

    return validator
        .isValidMemberEpoch(memberEpoch, metadata.getMemberEpoch())
        .map(
            ok -> {
              group.removeSession(memberId);
              if (metadata.isStaticMember()) {
                metadata.incrementMemberEpoch();
              }
              return metadata;
            });
  }

  private HeartbeatResponse heartbeat(final HeartbeatRequest request) {
    final var groupId = request.getGroupId();
    final var memberId = request.getMemberId();

    return validator
        .isGroupIdValid(groupId)
        .flatMap(ok -> validator.isActiveMember(groupId, memberId))
        .flatMap(ok -> processHeartbeat(request))
        .fold(errorCode -> new HeartbeatResponse().setErrorCode(errorCode), Function.identity());
  }

  private ConsumerGroup getOrCreateConsumerGroup(final String groupId) {
    final var group = registry.getGroup(groupId);
    if (group != null) {
      return group;
    }

    final var newGroup =
        new ConsumerGroup(groupId, partitionCount, this, clock, this::persistGroupMetadata);
    registry.addGroup(newGroup);
    return newGroup;
  }

  /** Replicates a group's membership/assignment after a rebalance so it survives failover. */
  private void persistGroupMetadata(final ConsumerGroup group) {
    coordinatorStream.replicateGroupMetadata(
        group.getGroupId(), GroupMetadataCodec.encode(group.toMetadata()));
  }

  /**
   * Rebuilds consumer groups from the coordinator stream's replicated metadata. Called once on
   * leader activation (after stream replay has populated the state), so consumers re-attach with
   * their existing epoch and assignment instead of a full rejoin storm.
   */
  private void restoreGroups() {
    final var snapshot = coordinatorStream.groupMetadataSnapshot();
    final var now = clock.instant();
    snapshot.forEach(
        (groupId, payload) ->
            getOrCreateConsumerGroup(groupId).restore(GroupMetadataCodec.decode(payload), now));
    if (!snapshot.isEmpty()) {
      LOG.info(
          "Coordinator partition {} — restored {} consumer group(s) from replicated metadata: {}",
          partitionId,
          snapshot.size(),
          snapshot.keySet());
    }
  }

  private MemberMetadata getOrCreateMemberMetadata(
      final ConsumerGroup group, final String instanceId) {
    if (instanceId == null || instanceId.isBlank()) {
      return MemberMetadata.dynamicMember(generateMemberId());
    }

    final var existing = group.getMemberByInstanceId(instanceId);
    return existing != null
        ? existing
        : MemberMetadata.staticMember(generateMemberId(), instanceId);
  }

  private Either<CoordinationErrorCode, HeartbeatResponse> processHeartbeat(
      final HeartbeatRequest request) {
    final var groupId = request.getGroupId();
    final var memberId = request.getMemberId();
    final var memberEpoch = request.getMemberEpoch();
    final var ownedPartitions = request.getOwnedPartitions();

    final var group = registry.getGroup(groupId);
    final var session = group.getSession(memberId);
    final var currentMemberEpoch = session.getMetadata().getMemberEpoch();

    return validator
        .isValidMemberEpoch(memberEpoch, currentMemberEpoch)
        .map(
            ok -> {
              final var delta = group.reconcileAssignment(session, ownedPartitions);
              session.setLastHeartbeat(clock.instant());
              return createHeartbeatResponse(group, session, delta);
            });
  }

  private HeartbeatResponse createHeartbeatResponse(
      final ConsumerGroup group,
      final GroupMemberSession session,
      final ReconciliationResult delta) {
    final var metadata = session.getMetadata();
    final var errorCode = group.isRebalancing() ? REBALANCE_IN_PROGRESS : NONE;

    return new HeartbeatResponse()
        .setErrorCode(errorCode)
        .setMemberId(metadata.getMemberId())
        .setMemberEpoch(metadata.getMemberEpoch())
        .setAssign(delta.assign())
        .setRevoke(delta.revoke())
        .setAssignment(delta.assignment())
        .setAssignmentEpoch(group.getAssignmentEpoch())
        .setCommittedOffsets(coordinatorStream.committedOffsets(group.getGroupId()));
  }

  @Override
  public String getName() {
    return "CoordinatorManager-" + partitionId;
  }

  @Override
  protected void onActorStarted() {
    // Committed-offset state lives in the coordinator stream's replicated ZeebeDb (loaded via
    // stream replay), so there is no local file to load here. Membership is rebuilt from the
    // replicated group metadata so consumers re-attach after a coordinator failover.
    restoreGroups();
    scheduleConsumerEviction();
    scheduleTopicBroadcast();
  }

  /**
   * Periodically broadcasts the topic registry to all brokers so each can reconcile its local topic
   * Raft groups. Only the registry shard's leader broadcasts (this manager runs on the leader
   * only), so there is exactly one broadcaster cluster-wide. Re-asserting the full registry on an
   * interval is the anti-entropy that lets a broker that missed an update still converge.
   */
  protected void scheduleTopicBroadcast() {
    if (topicAssignmentPublisher == null
        || partitionId != CoordinatorRouting.TOPIC_REGISTRY_SHARD) {
      return;
    }
    try {
      topicAssignmentPublisher.publish(
          TopicAssignmentGossip.encode(coordinatorStream.topicsSnapshot()));
    } catch (final Exception e) {
      LOG.warn("Failed to broadcast topic registry", e);
    }
    schedule(TOPIC_BROADCAST_INTERVAL, this::scheduleTopicBroadcast);
  }

  private String generateMemberId() {
    return String.valueOf(idGenerator.nextId());
  }
}
