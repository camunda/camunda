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
   * Request body for {@code POST /v1/events/{partitionId}/commit}.
   *
   * @param groupId consumer group identifier
   * @param consumerId consumer identifier
   * @param position log position to commit (idempotent if ≤ current committed offset)
   */
  public record CommitRequest(String groupId, String consumerId, long position) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record CommitResponse(String status, String error, String message) {

    public static CommitResponse ok() {
      return new CommitResponse("OK", null, null);
    }

    public static CommitResponse error(final String errorCode, final String message) {
      return new CommitResponse("ERROR", errorCode, message);
    }
  }

  // -------------------------------------------------------------------------
  // Subscribe

  /**
   * @deprecated Subscribe is superseded by heartbeat auto-registration. This DTO is retained for
   *     backward compatibility with the SBE codec and will be removed once the SBE schema drops the
   *     SubscribeResponse message type.
   */
  @Deprecated
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record SubscribeResponse(
      String status,
      List<Integer> assignedPartitions,
      Long generation,
      String error,
      String message) {

    public static SubscribeResponse ok(
        final List<Integer> assignedPartitions, final long generation) {
      return new SubscribeResponse("OK", assignedPartitions, generation, null, null);
    }

    public static SubscribeResponse error(final String errorCode, final String message) {
      return new SubscribeResponse("ERROR", null, null, errorCode, message);
    }
  }

  // -------------------------------------------------------------------------
  // Heartbeat

  /**
   * Request body for {@code POST /v1/consumers/{groupId}/{consumerId}/heartbeat}.
   *
   * @param epoch the epoch value last seen by the consumer; {@code null} or {@code 0} for a new
   *     consumer that has never received an epoch
   * @param ownedPartitions partition IDs the consumer currently holds
   */
  public record HeartbeatRequest(Long epoch, List<Integer> ownedPartitions) {}

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
      long epoch, List<Integer> revoke, List<Integer> assign, List<Integer> fullAssignment) {}

  // -------------------------------------------------------------------------
  // Ack

  /** Status values returned in an {@link AckResponse}. */
  public enum AckStatus {
    /**
     * ACK accepted; partition state advanced and consumer ownership recorded. Also returned for
     * stale-epoch ACKs, which are silently discarded without state mutation.
     */
    OK,
    /**
     * The supplied epoch does not match the coordinator's current epoch. The consumer should
     * re-synchronise by sending a heartbeat.
     */
    EPOCH_MISMATCH,
    /**
     * The {@code consumerId} is not registered in the specified group. The consumer should
     * re-register via a heartbeat before retrying.
     */
    CONSUMER_NOT_FOUND
  }

  /**
   * Request body for {@code POST /v1/consumers/{groupId}/{consumerId}/ack}.
   *
   * @param epoch the coordinator epoch from the heartbeat response that triggered this ACK
   * @param revoked partition IDs the consumer has stopped processing
   * @param assigned partition IDs the consumer has started processing
   */
  public record AckRequest(long epoch, List<Integer> revoked, List<Integer> assigned) {}

  /**
   * Response body for {@code POST /v1/consumers/{groupId}/{consumerId}/ack}.
   *
   * @param status result of the ACK; see {@link AckStatus}
   */
  public record AckResponse(AckStatus status) {

    public static AckResponse ok() {
      return new AckResponse(AckStatus.OK);
    }
  }

  // -------------------------------------------------------------------------
  // Latest position

  public record LatestPositionResponse(long position) {}

  // -------------------------------------------------------------------------
  // Error (publish/routing errors)

  public record ErrorResponse(String error, String message) {}
}
