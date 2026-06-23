/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.gateway.controller;

import io.camunda.eventbridge.gateway.dto.EventBridgeDtos.CreateTopicRequest;
import io.camunda.eventbridge.gateway.dto.EventBridgeDtos.TopicDto;
import io.camunda.eventbridge.protocol.request.coordination.CoordinationErrorCode;
import io.camunda.eventbridge.service.CoordinatorService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Topic management: {@code POST /v1/topics}, {@code DELETE /v1/topics/{name}}, {@code GET
 * /v1/topics}. Routes to the coordinator shard that owns the global topic registry. Registers the
 * desired state only — provisioning the topic's Raft group happens asynchronously (later
 * increment), so a freshly created topic is reported {@code CREATING} until then.
 *
 * <p>A {@code 503} means the coordinator was temporarily unreachable.
 */
@RestController
@RequestMapping("/v1/topics")
public class TopicController {

  private final CoordinatorService coordinatorService;

  public TopicController(final CoordinatorService coordinatorService) {
    this.coordinatorService = coordinatorService;
  }

  @PostMapping
  public CompletableFuture<ResponseEntity<Object>> createTopic(
      @RequestBody final CreateTopicRequest body) {
    final var request =
        new io.camunda.eventbridge.protocol.request.coordination.CreateTopicRequest()
            .setName(body.name() == null ? "" : body.name())
            .setPartitionCount(body.partitionCount() == null ? 0 : body.partitionCount())
            .setReplicationFactor(body.replicationFactor() == null ? 0 : body.replicationFactor());
    return coordinatorService
        .createTopic(request)
        .handleAsync(
            (res, error) -> {
              if (error != null) {
                return coordinatorUnavailable();
              }
              return ResponseEntity.status(createStatus(res.getErrorCode())).build();
            });
  }

  @DeleteMapping("/{name}")
  public CompletableFuture<ResponseEntity<Object>> deleteTopic(@PathVariable final String name) {
    final var request =
        new io.camunda.eventbridge.protocol.request.coordination.DeleteTopicRequest().setName(name);
    return coordinatorService
        .deleteTopic(request)
        .handleAsync(
            (res, error) -> {
              if (error != null) {
                return coordinatorUnavailable();
              }
              return ResponseEntity.status(deleteStatus(res.getErrorCode())).build();
            });
  }

  @GetMapping
  public CompletableFuture<ResponseEntity<Object>> listTopics() {
    return coordinatorService
        .listTopics()
        .handleAsync(
            (res, error) -> {
              if (error != null) {
                return coordinatorUnavailable();
              }
              return ResponseEntity.ok((Object) parseTopics(res.getPayload()));
            });
  }

  /**
   * Parses the registry payload (one {@code name;partitionCount;replicationFactor;status} per
   * line).
   */
  private static List<TopicDto> parseTopics(final String payload) {
    final List<TopicDto> topics = new ArrayList<>();
    if (payload == null || payload.isBlank()) {
      return topics;
    }
    for (final var line : payload.split("\n")) {
      if (line.isBlank()) {
        continue;
      }
      final var parts = line.split(";", 4);
      topics.add(
          new TopicDto(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), parts[3]));
    }
    return topics;
  }

  private static HttpStatus createStatus(final CoordinationErrorCode code) {
    return switch (code) {
      case NONE -> HttpStatus.CREATED;
      case INVALID_TOPIC -> HttpStatus.BAD_REQUEST;
      case TOPIC_ALREADY_EXISTS -> HttpStatus.CONFLICT;
      default -> HttpStatus.INTERNAL_SERVER_ERROR;
    };
  }

  private static HttpStatus deleteStatus(final CoordinationErrorCode code) {
    return switch (code) {
      case NONE -> HttpStatus.NO_CONTENT;
      case TOPIC_NOT_FOUND -> HttpStatus.NOT_FOUND;
      default -> HttpStatus.INTERNAL_SERVER_ERROR;
    };
  }

  private static ResponseEntity<Object> coordinatorUnavailable() {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
  }
}
