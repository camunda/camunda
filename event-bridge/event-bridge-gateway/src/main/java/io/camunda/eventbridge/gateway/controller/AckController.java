/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.gateway.controller;

import io.camunda.eventbridge.broker.actor.CoordinatorActor;
import io.camunda.eventbridge.gateway.dto.EventBridgeDtos.AckRequest;
import io.camunda.eventbridge.gateway.dto.EventBridgeDtos.AckResponse;
import io.camunda.eventbridge.gateway.dto.EventBridgeDtos.AckStatus;
import io.camunda.eventbridge.gateway.dto.EventBridgeDtos.ErrorResponse;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Handles consumer acknowledgement signals: {@code POST /v1/consumers/{groupId}/{consumerId}/ack}.
 *
 * <p>After receiving a heartbeat response containing {@code revoke} and/or {@code assign} lists (or
 * a {@code fullAssignment} reconciliation), the consumer sends an ACK confirming which partitions
 * it has stopped processing ({@code revoked}) and which it has started processing ({@code
 * assigned}). This drives the two-phase partition transfer state machine in the coordinator.
 *
 * <p>A stale-epoch ACK (epoch does not match the coordinator's current epoch) is silently discarded
 * by the coordinator — no error is surfaced to the consumer. The consumer self-corrects on the next
 * heartbeat via epoch advance and full reconciliation.
 *
 * <p>HTTP status semantics:
 *
 * <ul>
 *   <li>{@code 200 OK} — ACK received and processed (includes stale-epoch ACKs, which are discarded
 *       without state mutation).
 *   <li>{@code 400 Bad Request} — malformed request body.
 *   <li>{@code 404 Not Found} — {@code groupId} or {@code consumerId} does not exist.
 *   <li>{@code 503 Service Unavailable} — coordinator actor unreachable.
 * </ul>
 */
@RestController
@RequestMapping("/v1/consumers")
public class AckController {

  private final CoordinatorActor coordinatorActor;

  public AckController(final CoordinatorActor coordinatorActor) {
    this.coordinatorActor = coordinatorActor;
  }

  /**
   * Records a consumer ACK confirming revoked and assigned partitions.
   *
   * @param groupId consumer group identifier
   * @param consumerId consumer identifier
   * @param request ACK body containing epoch, revoked partition IDs, and assigned partition IDs
   */
  @PostMapping("/{groupId}/{consumerId}/ack")
  public ResponseEntity<?> ack(
      @PathVariable final String groupId,
      @PathVariable final String consumerId,
      @RequestBody final AckRequest request) {
    final List<Integer> revoked = request.revoked() != null ? request.revoked() : List.of();
    final List<Integer> assigned = request.assigned() != null ? request.assigned() : List.of();
    try {
      final var result =
          coordinatorActor.ack(groupId, consumerId, request.epoch(), revoked, assigned).get();
      final AckStatus dtoStatus =
          switch (result.status()) {
            case OK -> AckStatus.OK;
            case EPOCH_MISMATCH -> AckStatus.EPOCH_MISMATCH;
            case CONSUMER_NOT_FOUND -> AckStatus.CONSUMER_NOT_FOUND;
          };
      return ResponseEntity.ok(new AckResponse(dtoStatus));
    } catch (final ExecutionException e) {
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body(
              new ErrorResponse(
                  "COORDINATOR_UNAVAILABLE",
                  e.getCause() != null ? e.getCause().getMessage() : "Coordinator unavailable"));
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body(new ErrorResponse("COORDINATOR_UNAVAILABLE", "Coordinator unavailable"));
    }
  }
}
