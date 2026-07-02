/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.gateway.controller;

import io.camunda.eventbridge.api.proto.CommitRequest;
import io.camunda.eventbridge.api.proto.CommitResponse;
import io.camunda.eventbridge.api.proto.ConsumerHeartbeatRequest;
import io.camunda.eventbridge.api.proto.ConsumerHeartbeatResponse;
import io.camunda.eventbridge.api.proto.IntList;
import io.camunda.eventbridge.api.proto.JoinRequest;
import io.camunda.eventbridge.api.proto.JoinResponse;
import io.camunda.eventbridge.api.proto.OffsetFetchResult;
import io.camunda.eventbridge.api.proto.OffsetMap;
import io.camunda.eventbridge.protocol.request.coordination.CommitOffsetRequest;
import io.camunda.eventbridge.protocol.request.coordination.CoordinationErrorCode;
import io.camunda.eventbridge.protocol.request.coordination.HeartbeatRequest;
import io.camunda.eventbridge.protocol.request.coordination.JoinGroupRequest;
import io.camunda.eventbridge.protocol.request.coordination.LeaveGroupRequest;
import io.camunda.eventbridge.protocol.request.coordination.OffsetFetchRequest;
import io.camunda.eventbridge.protocol.topic.TopicPartition;
import io.camunda.eventbridge.service.CoordinatorService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consumer-group coordination: join, heartbeat, leave, and offset commit. Every request and
 * response uses a generated protobuf message and is content-negotiated: the same endpoint speaks
 * both {@code application/json} (humans/Postman, via {@code JsonFormat}) and {@code
 * application/x-protobuf} (the client SDK, binary). Spring picks the representation from the
 * request's {@code Accept}/{@code Content-Type} headers.
 *
 * <p>The first heartbeat from an unknown consumer auto-registers it with the coordinator — no prior
 * subscribe call is needed. The response carries the current epoch plus delta assignments ({@code
 * revoke}/{@code assign}) or, when an epoch advance has occurred, a full assignment list ({@code
 * assignment}) that the consumer must reconcile against.
 *
 * <p>A {@code 503 Service Unavailable} response means the coordinator is temporarily unreachable.
 */
@RestController
@RequestMapping("/v1/groups")
public class CoordinationController {

  private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
  private static final String PROTOBUF = "application/x-protobuf";

  private final CoordinatorService coordinatorService;

  public CoordinationController(final CoordinatorService coordinatorService) {
    this.coordinatorService = coordinatorService;
  }

  @PostMapping(
      value = "/{groupId}/members",
      consumes = {JSON, PROTOBUF},
      produces = {JSON, PROTOBUF})
  public CompletableFuture<ResponseEntity<Object>> joinGroup(
      @PathVariable final String groupId, @RequestBody final JoinRequest joinRequest) {

    final var request =
        new JoinGroupRequest()
            .setGroupId(groupId)
            .setTopics(new ArrayList<>(joinRequest.getTopicsList()))
            .setInstanceId(joinRequest.getInstanceId());
    return coordinatorService
        .joinGroup(request)
        .handleAsync(
            (res, error) -> {
              if (error != null) {
                return CoordinatorErrors.toResponse(error);
              }
              final var body =
                  JoinResponse.newBuilder()
                      .setErrorCode(nullToEmpty(res.getErrorCode().getId()))
                      .setMemberId(nullToEmpty(res.getMemberId()))
                      .setMemberEpoch(res.getMemberEpoch())
                      .build();
              // A member is created, so respond 201 Created with its assigned id and epoch.
              return ResponseEntity.status(HttpStatus.CREATED).body((Object) body);
            });
  }

  @PostMapping(
      value = "/{groupId}/members/{memberId}/heartbeat",
      consumes = {JSON, PROTOBUF},
      produces = {JSON, PROTOBUF})
  public CompletableFuture<ResponseEntity<Object>> heartbeat(
      @PathVariable final String groupId,
      @PathVariable final String memberId,
      @RequestBody final ConsumerHeartbeatRequest heartbeatRequest) {

    final List<TopicPartition> owned = new ArrayList<>();
    heartbeatRequest
        .getOwnedPartitionsMap()
        .forEach(
            (topic, partitions) ->
                partitions.getValuesList().forEach(p -> owned.add(new TopicPartition(topic, p))));
    final var request =
        new HeartbeatRequest()
            .setGroupId(groupId)
            .setMemberId(memberId)
            .setMemberEpoch(heartbeatRequest.getEpoch())
            .setOwnedPartitions(owned);
    return coordinatorService
        .heartbeat(request)
        .handleAsync(
            (res, error) -> {
              if (error != null) {
                return coordinatorUnavailable();
              }
              // Surface the coordinator's error code as an HTTP status so the client can act on it
              // (e.g. rejoin on a fenced/unknown member) rather than silently treating every
              // heartbeat as a success.
              final var body =
                  ConsumerHeartbeatResponse.newBuilder()
                      .setErrorCode(nullToEmpty(res.getErrorCode().getId()))
                      .setMemberId(nullToEmpty(res.getMemberId()))
                      .setMemberEpoch(res.getMemberEpoch())
                      .putAllRevoke(groupByTopic(res.getRevoke()))
                      .putAllAssign(groupByTopic(res.getAssign()))
                      .setAssignmentEpoch(res.getAssignmentEpoch())
                      .putAllAssignment(groupByTopic(res.getAssignment()))
                      .putAllCommittedOffsets(groupOffsetsByTopic(res.getCommittedOffsets()))
                      .build();
              return ResponseEntity.status(statusFor(res.getErrorCode())).body((Object) body);
            });
  }

  @DeleteMapping("/{groupId}/members/{memberId}")
  public CompletableFuture<ResponseEntity<Object>> leaveGroup(
      @PathVariable final String groupId,
      @PathVariable final String memberId,
      @RequestParam(name = "epoch", defaultValue = "0") final long epoch) {

    final var request =
        new LeaveGroupRequest().setGroupId(groupId).setMemberId(memberId).setMemberEpoch(epoch);
    return coordinatorService
        .leaveGroup(request)
        .handleAsync(
            (res, error) -> {
              if (error != null) {
                return CoordinatorErrors.toResponse(error);
              }
              // Removing a member is a delete: 204 No Content, no body. A fenced/unknown member is
              // still surfaced via the error mapping above.
              return ResponseEntity.noContent().build();
            });
  }

  @PostMapping(
      value = "/{groupId}/members/{memberId}/offsets",
      consumes = {JSON, PROTOBUF},
      produces = {JSON, PROTOBUF})
  public CompletableFuture<ResponseEntity<Object>> commit(
      @PathVariable final String groupId,
      @PathVariable final String memberId,
      @RequestBody final CommitRequest commitRequest) {

    final var request =
        new CommitOffsetRequest()
            .setGroupId(groupId)
            .setTopic(commitRequest.getTopic())
            .setMemberId(memberId)
            .setMemberEpoch(commitRequest.getMemberEpoch())
            .setPartitionId(commitRequest.getPartitionId())
            .setPosition(commitRequest.getPosition());
    return coordinatorService
        .commit(request)
        .handleAsync(
            (res, error) -> {
              // A fenced/unknown member must NOT read as a successful commit: the broker rejects
              // the
              // command and this maps it to a status (404/409) the client treats as "rejoin then
              // retry", instead of a silent 200.
              if (error != null) {
                return CoordinatorErrors.toResponse(error);
              }
              final var body =
                  CommitResponse.newBuilder()
                      .setErrorCode(nullToEmpty(res.getErrorCode().getId()))
                      .setCommittedPosition(res.getCommittedPosition())
                      .build();
              return ResponseEntity.ok((Object) body);
            });
  }

  @GetMapping
  public CompletableFuture<ResponseEntity<Object>> listGroups() {
    return coordinatorService
        .describeGroups(null)
        .handleAsync(
            (groups, error) ->
                error != null ? coordinatorUnavailable() : ResponseEntity.ok((Object) groups));
  }

  @GetMapping("/{groupId}")
  public CompletableFuture<ResponseEntity<Object>> describeGroup(
      @PathVariable final String groupId) {
    return coordinatorService
        .describeGroups(groupId)
        .handleAsync(
            (groups, error) -> {
              if (error != null) {
                return coordinatorUnavailable();
              }
              if (groups.isEmpty()) {
                return ResponseEntity.notFound().build();
              }
              return ResponseEntity.ok((Object) groups.get(0));
            });
  }

  @GetMapping(
      value = "/{groupId}/offsets",
      produces = {JSON, PROTOBUF})
  public CompletableFuture<ResponseEntity<Object>> offsets(
      @PathVariable final String groupId,
      @RequestParam(name = "partition", required = false) final List<String> partitions) {
    final var request = new OffsetFetchRequest().setGroupId(groupId);
    if (partitions != null) {
      partitions.forEach(partition -> request.addPartition(parsePartition(partition)));
    }
    return coordinatorService
        .offsetFetch(request)
        .handleAsync(
            (res, error) -> {
              if (error != null) {
                return coordinatorUnavailable();
              }
              final var body =
                  OffsetFetchResult.newBuilder()
                      .setErrorCode(nullToEmpty(res.getErrorCode().getId()))
                      .putAllCommittedOffsets(groupOffsetsByTopic(res.getCommittedOffsets()))
                      .build();
              return ResponseEntity.status(statusFor(res.getErrorCode())).body((Object) body);
            });
  }

  /** Groups a flat partition list into {@code topic → IntList} (partitions sorted). */
  private static Map<String, IntList> groupByTopic(final List<TopicPartition> partitions) {
    final Map<String, List<Integer>> byTopic = new TreeMap<>();
    partitions.forEach(
        p -> byTopic.computeIfAbsent(p.topic(), ignored -> new ArrayList<>()).add(p.partition()));
    final Map<String, IntList> result = new TreeMap<>();
    byTopic.forEach(
        (topic, ps) -> {
          ps.sort(Integer::compareTo);
          result.put(topic, IntList.newBuilder().addAllValues(ps).build());
        });
    return result;
  }

  /** Groups committed offsets into {@code topic → OffsetMap} (partitions sorted). */
  private static Map<String, OffsetMap> groupOffsetsByTopic(
      final Map<TopicPartition, Long> offsets) {
    final Map<String, Map<Integer, Long>> byTopic = new TreeMap<>();
    offsets.forEach(
        (tp, offset) ->
            byTopic
                .computeIfAbsent(tp.topic(), ignored -> new TreeMap<>())
                .put(tp.partition(), offset));
    final Map<String, OffsetMap> result = new TreeMap<>();
    byTopic.forEach(
        (topic, m) -> result.put(topic, OffsetMap.newBuilder().putAllOffsets(m).build()));
    return result;
  }

  private static String nullToEmpty(final String value) {
    return value == null ? "" : value;
  }

  /**
   * Maps a coordinator error code to the HTTP status the client reacts to.
   *
   * <ul>
   *   <li>{@code 200} — success, or a normal rebalance the client keeps polling through.
   *   <li>{@code 409} — the member is fenced/unknown (stale epoch, or the coordinator failed over
   *       and lost in-memory membership): the client must rejoin.
   *   <li>{@code 400} — malformed request (invalid group id).
   *   <li>{@code 500} — unexpected coordinator error.
   * </ul>
   */
  private static HttpStatus statusFor(final CoordinationErrorCode code) {
    return switch (code) {
      case NONE, REBALANCE_IN_PROGRESS -> HttpStatus.OK;
      case UNKNOWN_MEMBER_ID, FENCED_MEMBER_EPOCH, FENCED_MEMBER_ACTIVE, NOT_PARTITION_OWNER ->
          HttpStatus.CONFLICT;
      case INVALID_GROUP_ID -> HttpStatus.BAD_REQUEST;
      default -> HttpStatus.INTERNAL_SERVER_ERROR;
    };
  }

  /** The coordinator partition leader was unreachable; the client should retry. */
  private static ResponseEntity<Object> coordinatorUnavailable() {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
  }

  /** Parses a {@code topic:partition} query parameter into a {@link TopicPartition}. */
  private static TopicPartition parsePartition(final String partition) {
    final var separator = partition.lastIndexOf(':');
    if (separator <= 0 || separator == partition.length() - 1) {
      throw new IllegalArgumentException(
          "Expected partition filter as 'topic:partition' but got: " + partition);
    }
    final var topic = partition.substring(0, separator);
    final var id = Integer.parseInt(partition.substring(separator + 1));
    return new TopicPartition(topic, id);
  }
}
