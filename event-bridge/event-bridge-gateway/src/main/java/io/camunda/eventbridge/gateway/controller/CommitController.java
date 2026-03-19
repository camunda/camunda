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
import io.camunda.eventbridge.broker.actor.PublishActor;
import io.camunda.eventbridge.gateway.dto.EventBridgeDtos.CommitRequest;
import io.camunda.eventbridge.gateway.dto.EventBridgeDtos.CommitResponse;
import io.camunda.eventbridge.gateway.dto.EventBridgeDtos.ErrorResponse;
import io.camunda.eventbridge.gateway.dto.EventBridgeDtos.StaleGenerationResponse;
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
 * Handles offset commit requests: {@code POST /v1/events/{partitionId}/commit}.
 *
 * <p>Commits are idempotent: if the supplied {@code position} is at or below the consumer's current
 * committed offset the request is silently accepted with {@code 204 No Content}.
 *
 * <p>Validation order (per spec):
 *
 * <ol>
 *   <li>Partition exists — {@code 404 Not Found} / {@code PARTITION_NOT_FOUND}
 *   <li>Consumer is registered — {@code 400 Bad Request} / {@code CONSUMER_NOT_REGISTERED}
 *   <li>Generation matches — {@code 409 Conflict} / {@code STALE_GENERATION}
 *   <li>Partition is assigned to this consumer — {@code 403 Forbidden} / {@code
 *       PARTITION_NOT_ASSIGNED}
 * </ol>
 */
@RestController
@RequestMapping("/v1/events")
public class CommitController {

  private static final Logger LOG = LoggerFactory.getLogger(CommitController.class);

  private final Map<Integer, PublishActor> publishActors;
  private final CoordinatorActor coordinatorActor;

  public CommitController(
      final Map<Integer, PublishActor> publishActors, final CoordinatorActor coordinatorActor) {
    this.publishActors = publishActors;
    this.coordinatorActor = coordinatorActor;
  }

  /**
   * Records the consumer's committed offset for the given partition.
   *
   * @param partitionId the target partition
   * @param request body containing {@code groupId}, {@code consumerId}, {@code position}, and
   *     {@code generation}
   */
  @PostMapping("/{partitionId}/commit")
  public ResponseEntity<?> commitOffset(
      @PathVariable final int partitionId, @RequestBody final CommitRequest request) {

    if (!publishActors.containsKey(partitionId)) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(
              new ErrorResponse(
                  "PARTITION_NOT_FOUND", "Partition " + partitionId + " does not exist"));
    }

    // Validate consumer registration, generation, and partition assignment.
    final CoordinatorActor.AssignmentResult assignment;
    try {
      assignment = coordinatorActor.getAssignment(request.groupId(), request.consumerId()).get();
    } catch (final ExecutionException e) {
      if (e.getCause() instanceof ConsumerNotRegisteredException) {
        return ResponseEntity.badRequest()
            .body(CommitResponse.error("CONSUMER_NOT_REGISTERED", e.getCause().getMessage()));
      }
      LOG.error(
          "Coordinator error during commit for group={}, consumer={}",
          request.groupId(),
          request.consumerId(),
          e.getCause());
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body(CommitResponse.error("COORDINATOR_UNAVAILABLE", "Coordinator unavailable"));
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body(CommitResponse.error("COORDINATOR_UNAVAILABLE", "Coordinator unavailable"));
    }

    if (assignment.generation() != request.generation()) {
      return ResponseEntity.status(HttpStatus.CONFLICT)
          .body(StaleGenerationResponse.of(assignment.generation()));
    }

    if (!assignment.assignedPartitions().contains(partitionId)) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN)
          .body(
              new ErrorResponse(
                  "PARTITION_NOT_ASSIGNED",
                  "Partition " + partitionId + " is not assigned to this consumer"));
    }

    // Record the commit; idempotent if position <= current committed offset.
    try {
      coordinatorActor
          .commitOffset(request.groupId(), request.consumerId(), partitionId, request.position())
          .get();
      return ResponseEntity.noContent().build();
    } catch (final ExecutionException e) {
      if (e.getCause() instanceof ConsumerNotRegisteredException) {
        return ResponseEntity.badRequest()
            .body(CommitResponse.error("CONSUMER_NOT_REGISTERED", e.getCause().getMessage()));
      }
      LOG.error(
          "Error committing offset {} for group={}, consumer={}, partition={}",
          request.position(),
          request.groupId(),
          request.consumerId(),
          partitionId,
          e.getCause());
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body(CommitResponse.error("COORDINATOR_UNAVAILABLE", "Coordinator unavailable"));
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body(CommitResponse.error("COORDINATOR_UNAVAILABLE", "Coordinator unavailable"));
    }
  }
}
