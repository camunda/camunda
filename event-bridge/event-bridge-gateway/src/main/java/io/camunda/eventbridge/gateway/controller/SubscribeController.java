/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.gateway.controller;

import io.camunda.eventbridge.broker.actor.CoordinatorActor;
import io.camunda.eventbridge.gateway.dto.EventBridgeDtos.SubscribeResponse;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Handles consumer group subscribe requests: {@code POST
 * /v1/consumers/{groupId}/{consumerId}/subscribe}.
 *
 * <p>Registers the consumer with the coordinator (Broker-0) and triggers an immediate rebalance.
 * The response carries the resulting partition assignment and the new rebalance generation. Both
 * new consumers and consumers rejoining after a heartbeat timeout use this endpoint.
 */
@RestController
@RequestMapping("/v1/consumers")
public class SubscribeController {

  private static final Logger LOG = LoggerFactory.getLogger(SubscribeController.class);

  private final CoordinatorActor coordinatorActor;

  public SubscribeController(final CoordinatorActor coordinatorActor) {
    this.coordinatorActor = coordinatorActor;
  }

  /**
   * Registers {@code consumerId} in {@code groupId} and returns the resulting partition assignment.
   *
   * @param groupId consumer group identifier
   * @param consumerId unique consumer identifier within the group
   */
  @PostMapping("/{groupId}/{consumerId}/subscribe")
  public ResponseEntity<?> subscribe(
      @PathVariable final String groupId, @PathVariable final String consumerId) {
    try {
      final var result = coordinatorActor.subscribe(groupId, consumerId).get();
      return ResponseEntity.ok(
          SubscribeResponse.ok(result.assignedPartitions(), result.generation()));
    } catch (final ExecutionException e) {
      LOG.error("Subscribe failed for group={}, consumer={}", groupId, consumerId, e.getCause());
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body(
              SubscribeResponse.error(
                  "COORDINATOR_UNAVAILABLE",
                  e.getCause() != null ? e.getCause().getMessage() : "Coordinator unavailable"));
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body(SubscribeResponse.error("COORDINATOR_UNAVAILABLE", "Coordinator unavailable"));
    }
  }
}
