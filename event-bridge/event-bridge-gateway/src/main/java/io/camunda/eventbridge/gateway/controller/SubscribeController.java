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
import java.util.List;
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
 * Compatibility endpoint for the legacy subscribe flow: {@code POST
 * /v1/consumers/{groupId}/{consumerId}/subscribe}.
 *
 * <p>Auto-registers the consumer via a heartbeat (epoch=0, no owned partitions) and then waits for
 * the coordinator to complete the rebalance before returning. If a non-empty partition assignment
 * is not available within the bounded polling window, the response carries the current (possibly
 * empty) assignment with the current epoch so the caller can retry via heartbeats.
 *
 * <p>New clients should use {@code POST /v1/consumers/{groupId}/{consumerId}/heartbeat} instead.
 *
 * @deprecated Use the heartbeat endpoint for registration and assignment tracking.
 */
@Deprecated
@RestController
@RequestMapping("/v1/consumers")
public class SubscribeController {

  private static final Logger LOG = LoggerFactory.getLogger(SubscribeController.class);

  /** Maximum number of assignment-poll attempts after the initial heartbeat. */
  private static final int SUBSCRIBE_POLL_MAX_ATTEMPTS = 5;

  /** Wait between assignment-poll attempts (ms). */
  private static final long SUBSCRIBE_POLL_INTERVAL_MS = 200L;

  private final CoordinatorActor coordinatorActor;

  public SubscribeController(final CoordinatorActor coordinatorActor) {
    this.coordinatorActor = coordinatorActor;
  }

  /**
   * Registers {@code consumerId} in {@code groupId} and returns the resulting partition assignment.
   *
   * <p>Because the coordinator defers rebalance to its next loop tick, the first heartbeat may
   * return an empty assignment. This method retries {@link #SUBSCRIBE_POLL_MAX_ATTEMPTS} times (at
   * {@link #SUBSCRIBE_POLL_INTERVAL_MS} ms intervals) before returning whatever assignment is
   * available at that point. This preserves the blocking-subscribe contract expected by older
   * clients.
   *
   * @param groupId consumer group identifier
   * @param consumerId unique consumer identifier within the group
   */
  @PostMapping("/{groupId}/{consumerId}/subscribe")
  public ResponseEntity<?> subscribe(
      @PathVariable final String groupId, @PathVariable final String consumerId) {
    try {
      // Auto-register via heartbeat; rebalance may be deferred so assignment can be initially empty
      final var hbResult = coordinatorActor.heartbeat(groupId, consumerId, 0L, List.of()).get();

      // Prefer fullAssignment (returned when the consumer's epoch was behind) over delta assign
      List<Integer> partitions =
          !hbResult.fullAssignment().isEmpty() ? hbResult.fullAssignment() : hbResult.assign();
      long epoch = hbResult.epoch();

      // Poll getAssignment with a bounded retry to await the coordinator rebalance cycle
      for (int attempt = 0;
          attempt < SUBSCRIBE_POLL_MAX_ATTEMPTS && partitions.isEmpty();
          attempt++) {
        Thread.sleep(SUBSCRIBE_POLL_INTERVAL_MS); // NOSONAR — bounded wait in compat shim
        final var assignment = coordinatorActor.getAssignment(groupId, consumerId).get();
        partitions = assignment.assignedPartitions();
        epoch = assignment.epoch();
      }

      return ResponseEntity.ok(SubscribeResponse.ok(partitions, epoch));
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
