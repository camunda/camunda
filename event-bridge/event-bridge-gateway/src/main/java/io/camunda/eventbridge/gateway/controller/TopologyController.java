/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.gateway.controller;

import io.camunda.eventbridge.core.config.EventBridgeProperties;
import io.camunda.eventbridge.gateway.dto.EventBridgeDtos.PartitionTopology;
import io.camunda.eventbridge.gateway.dto.EventBridgeDtos.TopicTopology;
import io.camunda.eventbridge.gateway.dto.EventBridgeDtos.TopologyResponse;
import io.camunda.eventbridge.service.CoordinatorService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /v1/topology} — a human-readable view of the cluster: which brokers exist and, for
 * each topic, how its partitions are placed across them. The placement is the coordinator's
 * registry assignment (the desired state), so it reflects what each broker is told to host. Useful
 * for understanding a running multi-broker cluster.
 */
@RestController
@RequestMapping("/v1/topology")
public class TopologyController {

  private final CoordinatorService coordinatorService;
  private final int clusterSize;

  public TopologyController(
      final CoordinatorService coordinatorService, final EventBridgeProperties properties) {
    this.coordinatorService = coordinatorService;
    clusterSize = Math.max(1, properties.cluster().clusterSize());
  }

  @GetMapping
  public CompletableFuture<ResponseEntity<Object>> topology() {
    final var brokers = IntStream.range(0, clusterSize).boxed().toList();
    return coordinatorService
        .listTopics()
        .handleAsync(
            (res, error) -> {
              if (error != null) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
              }
              return ResponseEntity.ok(
                  (Object) new TopologyResponse(brokers, parseTopics(res.getPayload())));
            });
  }

  /**
   * Parses the registry payload: {@code name;partitionCount;replicationFactor;status;assignment}.
   */
  private static List<TopicTopology> parseTopics(final String payload) {
    final List<TopicTopology> topics = new ArrayList<>();
    if (payload == null || payload.isBlank()) {
      return topics;
    }
    for (final var line : payload.split("\n")) {
      if (line.isBlank()) {
        continue;
      }
      final var parts = line.split(";", 5);
      final var assignment = parts.length > 4 ? parts[4] : "";
      topics.add(
          new TopicTopology(
              parts[0],
              Integer.parseInt(parts[1]),
              Integer.parseInt(parts[2]),
              parts[3],
              parsePartitions(assignment)));
    }
    return topics;
  }

  /**
   * Parses {@code pid=n1,n2|pid=n1,n2|...} into partition placements (empty when not yet assigned).
   */
  private static List<PartitionTopology> parsePartitions(final String assignment) {
    final List<PartitionTopology> partitions = new ArrayList<>();
    if (assignment == null || assignment.isBlank()) {
      return partitions;
    }
    for (final var entry : assignment.split("\\|")) {
      final var eq = entry.indexOf('=');
      final var partitionId = Integer.parseInt(entry.substring(0, eq));
      final List<Integer> replicas = new ArrayList<>();
      final var ids = entry.substring(eq + 1);
      if (!ids.isBlank()) {
        for (final var id : ids.split(",")) {
          replicas.add(Integer.parseInt(id));
        }
      }
      partitions.add(new PartitionTopology(partitionId, replicas));
    }
    return partitions;
  }
}
