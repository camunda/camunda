/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.protocol.record.PartitionHealthStatus;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2")
public class StatusController {
  private final BrokerClient client;

  public StatusController(final BrokerClient client) {
    this.client = client;
  }

  @CamundaGetMapping(path = "/status")
  public ResponseEntity<Void> getStatus() {
    if (!hasAPartitionWithAHealthyLeader()) {
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }

    return ResponseEntity.noContent().build();
  }

  private boolean hasAPartitionWithAHealthyLeader() {
    final BrokerClusterState topology = client.getTopologyManager().getTopology();
    final List<Integer> partitions = topology.getPartitions();

    return partitions.stream()
        .anyMatch(
            partition -> {
              final int leader = topology.getLeaderForPartition(partition);
              return topology.getPartitionHealth(leader, partition)
                  == PartitionHealthStatus.HEALTHY;
            });
  }
}
