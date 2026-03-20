/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.gateway.controller;

import io.camunda.eventbridge.broker.actor.CoordinatorActor;
import io.camunda.eventbridge.gateway.dto.EventBridgeDtos.ErrorResponse;
import io.camunda.eventbridge.gateway.dto.EventBridgeDtos.HeartbeatRequest;
import io.camunda.eventbridge.gateway.dto.EventBridgeDtos.HeartbeatResponse;
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
 * Handles consumer liveness signals: {@code POST /v1/consumers/{groupId}/{consumerId}/heartbeat}.
 *
 * <p>The first heartbeat from an unknown consumer auto-registers it with the coordinator — no prior
 * subscribe call is needed. The response carries the current epoch plus delta assignments ({@code
 * revoke}/{@code assign}) or, when an epoch advance has occurred, a full assignment list ({@code
 * fullAssignment}) that the consumer must reconcile against.
 *
 * <p>A {@code 503 Service Unavailable} response means the coordinator is temporarily unreachable.
 */
@RestController
@RequestMapping("/v1/consumers")
public class HeartbeatController {

  private final CoordinatorActor coordinatorActor;

  public HeartbeatController(final CoordinatorActor coordinatorActor) {
    this.coordinatorActor = coordinatorActor;
  }

  /**
   * Records a heartbeat for the given consumer. Auto-registers the consumer on first contact and
   * returns the current epoch with any partition delta or full assignment.
   *
   * @param groupId consumer group identifier
   * @param consumerId consumer identifier
   * @param request heartbeat body (epoch + owned partitions); defaults apply when body is absent
   */
  @PostMapping("/{groupId}/{consumerId}/heartbeat")
  public ResponseEntity<?> heartbeat(
      @PathVariable final String groupId,
      @PathVariable final String consumerId,
      @RequestBody(required = false) final HeartbeatRequest request) {
    final long clientEpoch = request != null && request.epoch() != null ? request.epoch() : 0L;
    final List<Integer> ownedPartitions =
        request != null && request.ownedPartitions() != null
            ? request.ownedPartitions()
            : List.of();
    try {
      final var result =
          coordinatorActor.heartbeat(groupId, consumerId, clientEpoch, ownedPartitions).get();
      return ResponseEntity.ok(
          new HeartbeatResponse(
              result.epoch(), result.revoke(), result.assign(), result.fullAssignment()));
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
