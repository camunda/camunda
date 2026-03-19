/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.gateway.controller;

import io.camunda.eventbridge.broker.actor.PublishActor;
import io.camunda.eventbridge.gateway.dto.EventBridgeDtos.ErrorResponse;
import io.camunda.eventbridge.gateway.dto.EventBridgeDtos.LatestPositionResponse;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Handles partition metadata requests: {@code GET /v1/partitions/{partitionId}/latest-position}.
 *
 * <p>Returns the highest committed log position for the given partition. Clients that need
 * tail-start semantics (reading only new events, not from the beginning of the log) should call
 * this endpoint to obtain the starting position before their first {@code poll} call.
 *
 * <p>An empty partition (no events ever written) returns {@code { "position": 0 }}.
 */
@RestController
@RequestMapping("/v1/partitions")
public class PartitionController {

  private final Map<Integer, PublishActor> publishActors;

  public PartitionController(final Map<Integer, PublishActor> publishActors) {
    this.publishActors = publishActors;
  }

  /**
   * Returns the current log tail (latest committed position) for the given partition.
   *
   * @param partitionId the target partition
   */
  @GetMapping("/{partitionId}/latest-position")
  public ResponseEntity<?> getLatestPosition(@PathVariable final int partitionId) {
    final PublishActor actor = publishActors.get(partitionId);
    if (actor == null) {
      return ResponseEntity.badRequest()
          .body(
              new ErrorResponse(
                  "PARTITION_NOT_FOUND", "Partition " + partitionId + " does not exist"));
    }
    try {
      final long position = actor.getLatestPosition().get();
      return ResponseEntity.ok(new LatestPositionResponse(position));
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body(new ErrorResponse("LEADER_UNAVAILABLE", "Interrupted while reading log position"));
    } catch (final ExecutionException e) {
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body(new ErrorResponse("LEADER_UNAVAILABLE", e.getCause().getMessage()));
    }
  }
}
