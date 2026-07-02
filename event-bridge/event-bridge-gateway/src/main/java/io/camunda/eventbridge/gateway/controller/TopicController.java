/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.gateway.controller;

import io.camunda.eventbridge.api.proto.TopicCreateRequest;
import io.camunda.eventbridge.api.proto.TopicInfo;
import io.camunda.eventbridge.api.proto.TopicListResponse;
import io.camunda.eventbridge.protocol.request.coordination.CreateTopicRequest;
import io.camunda.eventbridge.protocol.request.coordination.DeleteTopicRequest;
import io.camunda.eventbridge.protocol.request.coordination.ReassignTopicRequest;
import io.camunda.eventbridge.service.CoordinatorService;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Topic management: {@code POST /v1/topics}, {@code DELETE /v1/topics/{name}}, {@code GET
 * /v1/topics}. Requests and the list response are generated protobuf messages, content-negotiated
 * between {@code application/json} (humans/Postman) and {@code application/x-protobuf} (the client
 * SDK). Routes to the coordinator shard that owns the global topic registry. Registers the desired
 * state only — provisioning the topic's Raft group happens asynchronously (later increment), so a
 * freshly created topic is reported {@code CREATING} until then.
 *
 * <p>A {@code 503} means the coordinator was temporarily unreachable.
 */
@RestController
@RequestMapping("/v1/topics")
public class TopicController {

  private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
  private static final String PROTOBUF = "application/x-protobuf";

  private final CoordinatorService coordinatorService;

  public TopicController(final CoordinatorService coordinatorService) {
    this.coordinatorService = coordinatorService;
  }

  @PostMapping(consumes = {JSON, PROTOBUF})
  public CompletableFuture<ResponseEntity<Object>> createTopic(
      @RequestBody final TopicCreateRequest body) {
    final var request =
        new CreateTopicRequest()
            .setName(body.getName())
            .setPartitionCount(body.getPartitionCount())
            .setReplicationFactor(body.getReplicationFactor());
    return coordinatorService
        .createTopic(request)
        .handleAsync(
            (res, error) -> {
              if (error != null) {
                return CoordinatorErrors.toResponse(error);
              }
              return ResponseEntity.status(HttpStatus.CREATED).build();
            });
  }

  @DeleteMapping("/{name}")
  public CompletableFuture<ResponseEntity<Object>> deleteTopic(@PathVariable final String name) {
    final var request = new DeleteTopicRequest().setName(name);
    return coordinatorService
        .deleteTopic(request)
        .handleAsync(
            (res, error) -> {
              if (error != null) {
                return CoordinatorErrors.toResponse(error);
              }
              return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
            });
  }

  @PostMapping("/{name}/reassign")
  public CompletableFuture<ResponseEntity<Object>> reassignTopic(
      @PathVariable final String name, @RequestParam final int replicationFactor) {
    final var request =
        new ReassignTopicRequest().setName(name).setReplicationFactor(replicationFactor);
    return coordinatorService
        .reassignTopic(request)
        .handleAsync(
            (res, error) -> {
              if (error != null) {
                return CoordinatorErrors.toResponse(error);
              }
              return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
            });
  }

  @GetMapping(produces = {JSON, PROTOBUF})
  public CompletableFuture<ResponseEntity<Object>> listTopics() {
    return coordinatorService
        .listTopics()
        .handleAsync(
            (res, error) -> {
              if (error != null) {
                return coordinatorUnavailable();
              }
              final var builder = TopicListResponse.newBuilder();
              res.getTopics()
                  .forEach(
                      topic ->
                          builder.addTopics(
                              TopicInfo.newBuilder()
                                  .setName(nullToEmpty(topic.name()))
                                  .setPartitionCount(topic.partitionCount())
                                  .setReplicationFactor(topic.replicationFactor())
                                  .setStatus(nullToEmpty(topic.status()))
                                  .build()));
              return ResponseEntity.ok((Object) builder.build());
            });
  }

  private static String nullToEmpty(final String value) {
    return value == null ? "" : value;
  }

  private static ResponseEntity<Object> coordinatorUnavailable() {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
  }
}
