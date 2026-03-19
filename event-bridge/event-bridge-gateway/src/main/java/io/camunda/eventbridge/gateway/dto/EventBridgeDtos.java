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
      Long generation,
      List<Integer> assignedPartitions,
      String error,
      String message) {

    public static PollResponse ok(
        final List<PollEvent> events, final long nextPosition, final long generation) {
      return new PollResponse("OK", events, nextPosition, generation, null, null, null);
    }

    public static PollResponse rebalance(
        final List<Integer> assignedPartitions, final long generation) {
      return new PollResponse(
          "REBALANCE_IN_PROGRESS", List.of(), null, generation, assignedPartitions, null, null);
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
   * @param generation rebalance generation returned by the most recent {@code subscribe} call
   */
  public record CommitRequest(String groupId, String consumerId, long position, long generation) {}

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

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record HeartbeatResponse(String status, Long generation, String error, String message) {

    public static HeartbeatResponse ok(final long generation) {
      return new HeartbeatResponse("OK", generation, null, null);
    }

    public static HeartbeatResponse error(final String errorCode, final String message) {
      return new HeartbeatResponse("ERROR", null, errorCode, message);
    }
  }

  // -------------------------------------------------------------------------
  // Latest position

  public record LatestPositionResponse(long position) {}

  // -------------------------------------------------------------------------
  // Error (publish/routing errors)

  public record ErrorResponse(String error, String message) {}

  /**
   * Error body for {@code 409 Conflict} when the client's {@code generation} parameter does not
   * match the broker's current generation for the consumer group.
   *
   * @param error always {@code "STALE_GENERATION"}
   * @param currentGeneration the generation the broker currently holds; client should re-subscribe
   */
  public record StaleGenerationResponse(String error, long currentGeneration) {
    public static StaleGenerationResponse of(final long currentGeneration) {
      return new StaleGenerationResponse("STALE_GENERATION", currentGeneration);
    }
  }
}
