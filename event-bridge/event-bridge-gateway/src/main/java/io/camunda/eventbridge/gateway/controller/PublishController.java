/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.gateway.controller;

import io.camunda.eventbridge.broker.actor.PublishActor;
import io.camunda.eventbridge.core.config.EventBridgeProperties;
import io.camunda.eventbridge.gateway.dto.EventBridgeDtos.ErrorResponse;
import io.camunda.eventbridge.gateway.dto.EventBridgeDtos.PublishRequest;
import io.camunda.eventbridge.gateway.dto.EventBridgeDtos.PublishResponse;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Handles event publish requests: {@code POST /v1/events/{partitionId}}.
 *
 * <p>Validates the batch size and individual event payload sizes configured via {@link
 * EventBridgeProperties}, then routes to the {@link PublishActor} for the requested partition. Each
 * batch is written as a single RAFT log entry; individual events within the batch receive
 * sequential log positions.
 */
@RestController
@RequestMapping("/v1/events")
public class PublishController {

  private static final Logger LOG = LoggerFactory.getLogger(PublishController.class);

  private final Map<Integer, PublishActor> publishActors;
  private final EventBridgeProperties properties;

  public PublishController(
      final Map<Integer, PublishActor> publishActors, final EventBridgeProperties properties) {
    this.publishActors = publishActors;
    this.properties = properties;
  }

  /**
   * Publishes a batch of base64-encoded events to the given partition.
   *
   * <p>Returns the log positions assigned to each event in input order.
   */
  @PostMapping("/{partitionId}")
  public ResponseEntity<?> publish(
      @PathVariable final int partitionId, @RequestBody final PublishRequest request) {

    if (request == null || request.events() == null || request.events().isEmpty()) {
      return ResponseEntity.badRequest()
          .body(new ErrorResponse("INVALID_REQUEST", "events must be a non-empty array"));
    }

    final int maxBatch = properties.publish().maxBatchSize();
    if (request.events().size() > maxBatch) {
      return ResponseEntity.badRequest()
          .body(
              new ErrorResponse(
                  "BATCH_TOO_LARGE",
                  "Batch exceeds the maximum allowed size of " + maxBatch + " events"));
    }

    final PublishActor actor = publishActors.get(partitionId);
    if (actor == null) {
      return ResponseEntity.badRequest()
          .body(
              new ErrorResponse(
                  "PARTITION_NOT_FOUND", "Partition " + partitionId + " does not exist"));
    }

    final List<byte[]> payloads = new ArrayList<>(request.events().size());
    for (final String encoded : request.events()) {
      final byte[] decoded;
      try {
        decoded = Base64.getDecoder().decode(encoded);
      } catch (final IllegalArgumentException e) {
        return ResponseEntity.badRequest()
            .body(new ErrorResponse("INVALID_REQUEST", "Event payload is not valid Base64"));
      }
      if (decoded.length > properties.publish().maxEventBytes()) {
        return ResponseEntity.badRequest()
            .body(
                new ErrorResponse(
                    "PAYLOAD_TOO_LARGE",
                    "Event payload exceeds maximum size of "
                        + properties.publish().maxEventBytes()
                        + " bytes"));
      }
      payloads.add(decoded);
    }

    try {
      final List<Long> positions = actor.publishBatch(payloads).get();
      return ResponseEntity.ok(new PublishResponse(positions));
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body(new ErrorResponse("LEADER_UNAVAILABLE", "Interrupted while writing to partition"));
    } catch (final ExecutionException e) {
      LOG.error("Failed to publish to partition {}", partitionId, e.getCause());
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body(new ErrorResponse("LEADER_UNAVAILABLE", e.getCause().getMessage()));
    }
  }
}
