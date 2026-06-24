/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.gateway.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/** HTTP request/response DTOs for the Event Bridge gateway API. */
public final class EventBridgeDtos {

  private EventBridgeDtos() {}

  // -------------------------------------------------------------------------
  // Publish

  public record PublishBatchResponse(List<Long> logPositions) {}

  // -------------------------------------------------------------------------
  // Poll

  public record PollEvent(long position, String payload) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record PollResponse(
      String status, List<PollEvent> events, Long nextPosition, String error, String message) {

    public static PollResponse ok(final List<PollEvent> events, final long nextPosition) {
      return new PollResponse("OK", events, nextPosition, null, null);
    }

    public static PollResponse error(final String errorCode, final String message) {
      return new PollResponse("ERROR", null, null, errorCode, message);
    }
  }

  // -------------------------------------------------------------------------
  // Commit

  /**
   * Request body for {@code POST /v1/groups/{groupId}/consumers/{memberId}/commit}. The group and
   * member come from the path.
   *
   * @param topic topic the partition belongs to
   * @param partitionId partition the offset belongs to
   * @param position next position to read (idempotent; only advances)
   * @param memberEpoch the committing member's epoch (for fencing stale commits)
   */
  public record CommitRequest(String topic, int partitionId, long position, long memberEpoch) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record CommitResponse(String errorCode, long committedPosition) {}

  // -------------------------------------------------------------------------
  // Heartbeat

  public record JoinGroupRequest(List<String> topics, String instanceId) {}

  public record JoinGroupResponse(String errorCode, String memberId, long memberEpoch) {}

  public record LeaveGroupRequest(String memberId, long memberEpoch) {}

  public record LeaveGroupResponse(String errorCode) {}

  /**
   * Request body for {@code POST /v1/consumers/{groupId}/{consumerId}/heartbeat}.
   *
   * @param epoch the epoch value last seen by the consumer; {@code null} or {@code 0} for a new
   *     consumer that has never received an epoch
   * @param ownedPartitions the partitions the consumer currently holds, grouped {@code topic →
   *     [partition,...]}
   */
  public record HeartbeatRequest(
      String memberId, Long epoch, Map<String, List<Integer>> ownedPartitions) {}

  /**
   * Response body for {@code POST /v1/consumers/{groupId}/{consumerId}/heartbeat}.
   *
   * <p>Field names must match exactly what the client parses ({@code Consumer.sendHeartbeat}):
   * {@code memberEpoch} drives full-reconciliation detection and {@code assignment} carries the
   * full target set. They were previously named {@code epoch}/{@code fullAssignment}, which
   * silently disabled the client's full-reconciliation path.
   *
   * @param memberEpoch coordinator's current epoch; always {@code >= request.epoch}
   * @param revoke partitions the consumer must stop processing and acknowledge
   * @param assign partitions the consumer should start processing and acknowledge
   * @param assignment complete target assignment; applied when the coordinator epoch has advanced
   *     since the consumer's last heartbeat, in which case the consumer reconciles fully from it
   */
  public record HeartbeatResponse(
      String errorCode,
      String memberId,
      long memberEpoch,
      Map<String, List<Integer>> revoke,
      Map<String, List<Integer>> assign,
      long assignmentEpoch,
      Map<String, List<Integer>> assignment,
      Map<String, Map<Integer, Long>> committedOffsets) {}

  /**
   * Response body for {@code GET /v1/groups/{groupId}/offsets} — a group's committed offsets
   * grouped {@code topic → (partition → offset)}.
   */
  public record OffsetFetchResponse(
      String errorCode, Map<String, Map<Integer, Long>> committedOffsets) {}

  public record LatestPositionResponse(long position) {}

  // -------------------------------------------------------------------------
  // Topics

  /** Body for {@code POST /v1/topics}. */
  public record CreateTopicRequest(
      String name, Integer partitionCount, Integer replicationFactor) {}

  /** A topic as held in the registry. */
  public record TopicDto(String name, int partitionCount, int replicationFactor, String status) {}

  // -------------------------------------------------------------------------
  // Topology

  /**
   * Cluster topology as currently assigned: which brokers exist, and for each topic, how its
   * partitions are placed across them. Reflects the coordinator's registry (the desired
   * assignment).
   */
  public record TopologyResponse(List<Integer> brokers, List<TopicTopology> topics) {}

  public record TopicTopology(
      String name,
      int partitionCount,
      int replicationFactor,
      String status,
      List<PartitionTopology> partitions) {}

  /** A single topic partition and the broker node ids that replicate it. */
  public record PartitionTopology(int partitionId, List<Integer> replicas) {}

  // -------------------------------------------------------------------------
  // Latest position

  public record ErrorResponse(String error, String message) {}
}
