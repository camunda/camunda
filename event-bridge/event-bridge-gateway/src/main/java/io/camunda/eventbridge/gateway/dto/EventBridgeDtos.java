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
      String status,
      List<PollEvent> events,
      Long nextPosition,
      Long epoch,
      List<Integer> assignedPartitions,
      String error,
      String message) {

    public static PollResponse ok(
        final List<PollEvent> events, final long nextPosition, final long epoch) {
      return new PollResponse("OK", events, nextPosition, epoch, null, null, null);
    }

    public static PollResponse rebalance(final List<Integer> assignedPartitions, final long epoch) {
      return new PollResponse(
          "REBALANCE_IN_PROGRESS", List.of(), null, epoch, assignedPartitions, null, null);
    }

    public static PollResponse error(final String errorCode, final String message) {
      return new PollResponse("ERROR", null, null, null, null, errorCode, message);
    }
  }

  // -------------------------------------------------------------------------
  // Commit

  /**
   * Request body for {@code POST /v1/groups/{groupId}/consumers/{memberId}/commit}. The group and
   * member come from the path.
   *
   * @param partitionId partition the offset belongs to
   * @param position next position to read (idempotent; only advances)
   * @param memberEpoch the committing member's epoch (for fencing stale commits)
   */
  public record CommitRequest(int partitionId, long position, long memberEpoch) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record CommitResponse(String errorCode, long committedPosition) {}

  // -------------------------------------------------------------------------
  // Heartbeat

  public record JoinGroupRequest(String instanceId) {}

  public record JoinGroupResponse(String errorCode, String memberId, long memberEpoch) {}

  public record LeaveGroupRequest(String memberId, long memberEpoch) {}

  public record LeaveGroupResponse(String errorCode) {}

  /**
   * Request body for {@code POST /v1/consumers/{groupId}/{consumerId}/heartbeat}.
   *
   * @param epoch the epoch value last seen by the consumer; {@code null} or {@code 0} for a new
   *     consumer that has never received an epoch
   * @param ownedPartitions partition IDs the consumer currently holds
   */
  public record HeartbeatRequest(String memberId, Long epoch, List<Integer> ownedPartitions) {}

  /**
   * Response body for {@code POST /v1/consumers/{groupId}/{consumerId}/heartbeat}.
   *
   * @param epoch coordinator's current epoch; always {@code >= request.epoch}
   * @param revoke partitions the consumer must stop processing and acknowledge
   * @param assign partitions the consumer should start processing and acknowledge
   * @param fullAssignment complete target assignment; non-empty only when the coordinator epoch has
   *     advanced since the consumer's last heartbeat, in which case the consumer must reconcile
   *     fully from this list
   */
  public record HeartbeatResponse(
      String errorCode,
      String memberId,
      long epoch,
      List<Integer> revoke,
      List<Integer> assign,
      long assignmentEpoch,
      List<Integer> fullAssignment,
      java.util.Map<Integer, Long> committedOffsets) {}

  public record LatestPositionResponse(long position) {}

  // -------------------------------------------------------------------------
  // Latest position

  public record ErrorResponse(String error, String message) {}
}
