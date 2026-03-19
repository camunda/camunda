/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.gateway.controller;

import io.camunda.eventbridge.broker.actor.CoordinatorActor;
import io.camunda.eventbridge.broker.actor.CoordinatorActor.ConsumerNotRegisteredException;
import io.camunda.eventbridge.gateway.dto.EventBridgeDtos.HeartbeatResponse;
import java.util.concurrent.ExecutionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Handles consumer liveness signals: {@code POST /v1/consumers/{groupId}/{consumerId}/heartbeat}.
 *
 * <p>The response carries the coordinator's current generation for the group. A changed generation
 * (compared to the generation the consumer last received from {@code subscribe} or a previous
 * heartbeat) signals that a rebalance has occurred and the consumer must re-subscribe.
 *
 * <p>A {@code 400 Bad Request} / {@code CONSUMER_NOT_REGISTERED} response means the consumer's
 * heartbeat timeout elapsed and it was evicted; the consumer must call {@code subscribe} again
 * before sending further heartbeats.
 */
@RestController
@RequestMapping("/v1/consumers")
public class HeartbeatController {

  private final CoordinatorActor coordinatorActor;

  public HeartbeatController(final CoordinatorActor coordinatorActor) {
    this.coordinatorActor = coordinatorActor;
  }

  /**
   * Records a heartbeat for the given consumer and returns the current rebalance generation.
   *
   * @param groupId consumer group identifier
   * @param consumerId consumer identifier
   */
  @PostMapping("/{groupId}/{consumerId}/heartbeat")
  public ResponseEntity<?> heartbeat(
      @PathVariable final String groupId, @PathVariable final String consumerId) {
    try {
      final long generation = coordinatorActor.heartbeat(groupId, consumerId).get();
      return ResponseEntity.ok(HeartbeatResponse.ok(generation));
    } catch (final ExecutionException e) {
      if (e.getCause() instanceof ConsumerNotRegisteredException) {
        return ResponseEntity.badRequest()
            .body(HeartbeatResponse.error("CONSUMER_NOT_REGISTERED", e.getCause().getMessage()));
      }
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body(HeartbeatResponse.error("COORDINATOR_UNAVAILABLE", e.getCause().getMessage()));
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body(HeartbeatResponse.error("COORDINATOR_UNAVAILABLE", "Coordinator unavailable"));
    }
  }
}
