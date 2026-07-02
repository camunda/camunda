/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.gateway.controller;

import io.camunda.eventbridge.api.proto.PartitionTopology;
import io.camunda.eventbridge.api.proto.TopicTopology;
import io.camunda.eventbridge.api.proto.TopologyResponse;
import io.camunda.eventbridge.core.config.EventBridgeProperties;
import io.camunda.eventbridge.protocol.request.coordination.ListTopicsResponse;
import io.camunda.eventbridge.service.CoordinatorService;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /v1/topology} — a human-readable view of the cluster: which brokers exist and, for
 * each topic, how its partitions are placed across them. The placement is the coordinator's
 * registry assignment (the desired state), so it reflects what each broker is told to host. Useful
 * for understanding a running multi-broker cluster.
 *
 * <p>The response is a generated protobuf message, content-negotiated between {@code
 * application/json} (humans/Postman) and {@code application/x-protobuf}.
 */
@RestController
@RequestMapping("/v1/topology")
public class TopologyController {

  private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
  private static final String PROTOBUF = "application/x-protobuf";

  private final CoordinatorService coordinatorService;
  private final int clusterSize;

  public TopologyController(
      final CoordinatorService coordinatorService, final EventBridgeProperties properties) {
    this.coordinatorService = coordinatorService;
    clusterSize = Math.max(1, properties.cluster().clusterSize());
  }

  @GetMapping(produces = {JSON, PROTOBUF})
  public CompletableFuture<ResponseEntity<Object>> topology() {
    final var brokers = IntStream.range(0, clusterSize).boxed().toList();
    return coordinatorService
        .listTopics()
        .handleAsync(
            (res, error) -> {
              if (error != null) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
              }
              final var response =
                  TopologyResponse.newBuilder()
                      .addAllBrokers(brokers)
                      .addAllTopics(toTopologies(res));
              return ResponseEntity.ok((Object) response.build());
            });
  }

  private static Iterable<TopicTopology> toTopologies(final ListTopicsResponse response) {
    return response.getTopics().stream()
        .map(
            topic -> {
              final var builder =
                  TopicTopology.newBuilder()
                      .setName(nullToEmpty(topic.name()))
                      .setPartitionCount(topic.partitionCount())
                      .setReplicationFactor(topic.replicationFactor())
                      .setStatus(nullToEmpty(topic.status()));
              topic
                  .assignment()
                  .forEach(
                      (partitionId, replicas) ->
                          builder.addPartitions(
                              PartitionTopology.newBuilder()
                                  .setPartitionId(partitionId)
                                  .addAllReplicas(replicas)
                                  .build()));
              return builder.build();
            })
        .toList();
  }

  private static String nullToEmpty(final String value) {
    return value == null ? "" : value;
  }
}
