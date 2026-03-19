/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.gateway.controller;

import io.camunda.eventbridge.broker.actor.CoordinatorActor;
import io.camunda.eventbridge.broker.actor.PublishActor;
import io.camunda.eventbridge.broker.actor.PublishActor.PollRecordsResult;
import io.camunda.eventbridge.core.config.EventBridgeProperties;
import io.camunda.eventbridge.gateway.dto.EventBridgeDtos.PollEvent;
import io.camunda.eventbridge.gateway.dto.EventBridgeDtos.PollResponse;
import io.camunda.eventbridge.gateway.dto.EventBridgeDtos.StaleGenerationResponse;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Handles event poll requests: {@code GET /v1/events/{partitionId}/poll}.
 *
 * <p>Supports long-polling: when {@code serverWaitMs > 0} and no records are available at the
 * requested position, the broker parks the response thread until a new entry is written or the wait
 * window elapses. Parking uses actor async primitives; no ActorScheduler CPU thread is blocked.
 *
 * <p>Generation staleness is validated once at request-arrival time. If the generation supplied by
 * the client does not match the broker's current generation for the consumer group, the request is
 * rejected with {@code 409 Conflict}. If a rebalance occurs <em>during</em> a long-poll wait, the
 * response returns {@code 200 OK} with the updated generation; the client detects the change and
 * re-subscribes.
 */
@RestController
@RequestMapping("/v1/events")
public class PollController {

  private static final Logger LOG = LoggerFactory.getLogger(PollController.class);

  private final Map<Integer, PublishActor> publishActors;
  private final CoordinatorActor coordinatorActor;
  private final EventBridgeProperties properties;

  public PollController(
      final Map<Integer, PublishActor> publishActors,
      final CoordinatorActor coordinatorActor,
      final EventBridgeProperties properties) {
    this.publishActors = publishActors;
    this.coordinatorActor = coordinatorActor;
    this.properties = properties;
  }

  /**
   * Pulls the next batch of events from the given partition.
   *
   * @param partitionId the target partition
   * @param groupId consumer group identifier (must be registered)
   * @param consumerId consumer identifier (must be registered)
   * @param fromPosition starting log position; {@code -1} resolves to the oldest retained position
   * @param maxRecords maximum number of records to return (default 100; must be &ge; 1)
   * @param serverWaitMs server-side long-poll wait in milliseconds (default 0; clamped to
   *     configured ceiling)
   * @param generation rebalance generation from the most recent {@code subscribe} call
   */
  @GetMapping("/{partitionId}/poll")
  public ResponseEntity<?> poll(
      @PathVariable final int partitionId,
      @RequestParam final String groupId,
      @RequestParam final String consumerId,
      @RequestParam final long fromPosition,
      @RequestParam(defaultValue = "100") final int maxRecords,
      @RequestParam(defaultValue = "0") final int serverWaitMs,
      @RequestParam final long generation) {

    if (maxRecords < 1) {
      return ResponseEntity.badRequest()
          .body(PollResponse.error("INVALID_PARAMETER", "maxRecords must be >= 1"));
    }

    final PublishActor actor = publishActors.get(partitionId);
    if (actor == null) {
      return ResponseEntity.badRequest()
          .body(
              PollResponse.error(
                  "PARTITION_NOT_FOUND", "Partition " + partitionId + " does not exist"));
    }

    // Validate generation at request-arrival time (spec §5.2).
    final CoordinatorActor.AssignmentResult assignment;
    try {
      assignment = coordinatorActor.getAssignment(groupId, consumerId).get();
    } catch (final ExecutionException e) {
      return ResponseEntity.badRequest()
          .body(PollResponse.error("CONSUMER_NOT_REGISTERED", e.getCause().getMessage()));
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body(PollResponse.error("COORDINATOR_UNAVAILABLE", "Coordinator unavailable"));
    }

    if (assignment.generation() != generation) {
      return ResponseEntity.status(HttpStatus.CONFLICT)
          .body(StaleGenerationResponse.of(assignment.generation()));
    }

    // Try to read records immediately before parking.
    final PollRecordsResult immediateResult;
    try {
      immediateResult = actor.pollRecords(fromPosition, maxRecords).get();
    } catch (final ExecutionException e) {
      LOG.error(
          "Error reading partition {} at position {}", partitionId, fromPosition, e.getCause());
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body(PollResponse.error("LEADER_UNAVAILABLE", e.getCause().getMessage()));
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body(PollResponse.error("LEADER_UNAVAILABLE", "Interrupted while reading partition"));
    }

    if (immediateResult instanceof PollRecordsResult.PositionTruncated truncated) {
      return ResponseEntity.badRequest()
          .body(
              PollResponse.error(
                  "POSITION_TRUNCATED",
                  "Position has been truncated; oldest available position is "
                      + truncated.oldestAvailablePosition()));
    }

    final var success = (PollRecordsResult.Success) immediateResult;

    // Records are available — return immediately without waiting.
    if (!success.records().isEmpty()) {
      return buildOkResponse(success, generation);
    }

    // No records yet; optionally park via long-poll.
    if (serverWaitMs > 0) {
      final int clamped = Math.min(serverWaitMs, properties.broker().longPoll().maxWaitMs());
      try {
        actor.awaitRecords(fromPosition, clamped).get();
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        // Fall through and return whatever is in the log now.
      } catch (final ExecutionException e) {
        LOG.warn("Long-poll wait error on partition {}", partitionId, e.getCause());
        // Fall through and return whatever is in the log now.
      }

      // Read again after wake-up.
      try {
        final var afterWait = actor.pollRecords(fromPosition, maxRecords).get();
        if (afterWait instanceof PollRecordsResult.PositionTruncated truncated) {
          return ResponseEntity.badRequest()
              .body(
                  PollResponse.error(
                      "POSITION_TRUNCATED",
                      "Position has been truncated; oldest available position is "
                          + truncated.oldestAvailablePosition()));
        }
        final var afterSuccess = (PollRecordsResult.Success) afterWait;

        // Re-check generation after the wait: a changed generation signals a rebalance.
        final long currentGeneration = getCurrentGeneration(groupId, consumerId, generation);
        return buildOkResponse(afterSuccess, currentGeneration);
      } catch (final ExecutionException e) {
        LOG.error("Error reading partition {} after long-poll wait", partitionId, e.getCause());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(PollResponse.error("LEADER_UNAVAILABLE", e.getCause().getMessage()));
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(PollResponse.error("LEADER_UNAVAILABLE", "Interrupted while reading partition"));
      }
    }

    // serverWaitMs == 0 and no records — return empty response with current tail.
    return buildOkResponse(success, generation);
  }

  // -------------------------------------------------------------------------
  // Helpers

  private ResponseEntity<PollResponse> buildOkResponse(
      final PollRecordsResult.Success result, final long generation) {
    final List<PollEvent> events =
        result.records().stream()
            .map(r -> new PollEvent(r.position(), Base64.getEncoder().encodeToString(r.payload())))
            .toList();
    return ResponseEntity.ok(PollResponse.ok(events, result.nextPosition(), generation));
  }

  /**
   * Fetches the current generation for the consumer group after a long-poll wait. Falls back to the
   * supplied {@code fallback} if the coordinator is unreachable.
   */
  private long getCurrentGeneration(
      final String groupId, final String consumerId, final long fallback) {
    try {
      return coordinatorActor.getAssignment(groupId, consumerId).get().generation();
    } catch (final ExecutionException | InterruptedException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      return fallback;
    }
  }
}
