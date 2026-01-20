/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.atomix.primitive.partition.PartitionId;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import io.camunda.zeebe.broker.system.configuration.FlowControlCfg;
import io.camunda.zeebe.gateway.admin.BrokerAdminRequest;
import io.camunda.zeebe.logstreams.impl.flowcontrol.LimitSerializer;
import io.camunda.zeebe.shared.management.FlowControlEndpoint.FlowControlService;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.agrona.collections.IntHashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class FlowControlServiceImpl implements FlowControlService {
  private static final Logger LOG = LoggerFactory.getLogger(FlowControlServiceImpl.class);
  private final BrokerClient client;

  @Autowired
  public FlowControlServiceImpl(final BrokerClient client) {
    this.client = client;
  }

  @Override
  public CompletableFuture<Map<Integer, JsonNode>> get() {
    final var topology = client.getTopologyManager().getTopology();
    final var futures =
        topology.getPartitions().stream()
            .map(partition -> fetchFlowConfigOnPartition(topology, partition))
            .toList();
    return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
        .thenApply(
            ignored ->
                futures.stream()
                    .map(CompletableFuture::join)
                    .collect(
                        Collectors.toMap(
                            FlowControlStatus::partitionId, FlowControlStatus::flowControlConfig)));
  }

  @Override
  public CompletableFuture<Map<Integer, JsonNode>> set(final FlowControlCfg flowControlCfg) {
    LOG.info("Setting flow control configuration to {}", flowControlCfg);

    final byte[] configuration;
    try {
      configuration = flowControlCfg.serialize();
    } catch (final JsonProcessingException e) {
      return CompletableFuture.failedFuture(e);
    }

    final var topology = client.getTopologyManager().getTopology();
    final var results =
        topology.getPartitions().stream()
            .map(
                partition ->
                    broadcastOnPartition(
                        topology,
                        partition,
                        request -> request.setFlowControlConfiguration(configuration)))
            .toArray(CompletableFuture<?>[]::new);
    return CompletableFuture.allOf(results).thenCompose(ignored -> get());
  }

  private CompletableFuture<Void> broadcastOnPartition(
      final BrokerClusterState topology,
      final Integer partitionId,
      final Consumer<BrokerAdminRequest> configureRequest) {

    final var members = getMembers(topology, partitionId);

    final var requests =
        members.stream()
            .map(
                brokerId -> {
                  final var request = new BrokerAdminRequest();
                  request.setBrokerId(brokerId);
                  request.setPartitionId(partitionId);
                  configureRequest.accept(request);
                  return client.sendRequest(request);
                })
            .toArray(CompletableFuture<?>[]::new);
    return CompletableFuture.allOf(requests);
  }

  private CompletableFuture<FlowControlStatus> fetchFlowConfigOnPartition(
      final BrokerClusterState topology, final Integer partitionId) {
    final var brokerId =
        topology.getLeaderForPartition(new PartitionId("raft-partition", partitionId));
    final var request = new BrokerAdminRequest();
    request.setBrokerId(brokerId);
    request.setPartitionId(partitionId);
    request.getFLowControlConfiguration();

    return client
        .sendRequest(request)
        .thenApply(
            response ->
                new FlowControlStatus(
                    partitionId, LimitSerializer.deserialize(response.getResponse().getPayload())));
  }

  private IntHashSet getMembers(final BrokerClusterState topology, final Integer partitionId) {
    final var leader =
        topology.getLeaderForPartition(new PartitionId("raft-partition", partitionId));
    final var followers =
        Optional.ofNullable(
                topology.getFollowersForPartition(new PartitionId("raft-partition", partitionId)))
            .orElseGet(Set::of);
    final var inactive =
        Optional.ofNullable(
                topology.getInactiveNodesForPartition(
                    new PartitionId("raft-partition", partitionId)))
            .orElseGet(Set::of);

    final var members = new IntHashSet(topology.getReplicationFactor());
    members.add(leader);
    members.addAll(followers);
    members.addAll(inactive);
    return members;
  }

  record FlowControlStatus(int partitionId, JsonNode flowControlConfig) {}
}
