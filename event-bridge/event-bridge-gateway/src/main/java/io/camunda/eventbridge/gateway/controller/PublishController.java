/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.gateway.controller;

import static org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE;

import io.camunda.eventbridge.broker.actor.PublishActor;
import io.camunda.eventbridge.core.EventData;
import io.camunda.eventbridge.core.EventDataBatch;
import io.camunda.eventbridge.core.config.EventBridgeProperties;
import io.camunda.eventbridge.gateway.dto.EventBridgeDtos.ErrorResponse;
import io.camunda.eventbridge.gateway.dto.EventBridgeDtos.PublishBatchResponse;
import java.util.ArrayList;
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
 * <p>Accepts {@code Content-Type: application/octet-stream} only. The request body must be a valid
 * {@link EventDataBatch} binary serialisation. Validates batch-level and per-event size limits
 * configured via {@link EventBridgeProperties}, then routes to the {@link PublishActor} for the
 * requested partition. Each batch is written as a single RAFT log entry; individual events within
 * the batch receive sequential log positions.
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
   * Publishes a binary-framed batch of events to the given partition.
   *
   * <p>Returns the log positions assigned to each event in input order.
   */
  @PostMapping(value = "/{partitionId}", consumes = APPLICATION_OCTET_STREAM_VALUE)
  public ResponseEntity<?> publish(
      @PathVariable final int partitionId, @RequestBody final byte[] requestBody) {

    // Deserialise and validate the binary framing
    final EventDataBatch batch;
    try {
      batch = EventDataBatch.fromBytes(requestBody);
    } catch (final IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_REQUEST", e.getMessage()));
    }

    // Batch-level event count limit (uses framing header — no event iteration)
    final int maxBatchSize = properties.publish().maxBatchSize();
    if (batch.getCount() > maxBatchSize) {
      return ResponseEntity.badRequest()
          .body(
              new ErrorResponse(
                  "PAYLOAD_TOO_LARGE",
                  "Batch exceeds maximum allowed size of " + maxBatchSize + " events"));
    }

    // Batch-level total payload size limit (uses framing header — no event iteration)
    final int maxBatchBytes = properties.publish().maxBatchBytes();
    if (batch.getSizeInBytes() > maxBatchBytes) {
      return ResponseEntity.badRequest()
          .body(
              new ErrorResponse(
                  "PAYLOAD_TOO_LARGE",
                  "Batch payload exceeds maximum of " + maxBatchBytes + " bytes"));
    }

    // Partition lookup
    final PublishActor actor = publishActors.get(partitionId);
    if (actor == null) {
      return ResponseEntity.badRequest()
          .body(
              new ErrorResponse(
                  "PARTITION_NOT_FOUND", "Partition " + partitionId + " does not exist"));
    }

    // Per-event size limit — iterates events once; also extracts payloads for the actor call
    final int maxEventBytes = properties.publish().maxEventBytes();
    final List<byte[]> payloads = new ArrayList<>(batch.getCount());
    for (final EventData event : batch.getEvents()) {
      if (event.sizeInBytes() > maxEventBytes) {
        return ResponseEntity.badRequest()
            .body(
                new ErrorResponse(
                    "PAYLOAD_TOO_LARGE",
                    "Event payload exceeds maximum size of " + maxEventBytes + " bytes"));
      }
      payloads.add(event.body());
    }

    try {
      final List<Long> positions = actor.publishBatch(payloads).get();
      return ResponseEntity.ok(new PublishBatchResponse(positions));
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
