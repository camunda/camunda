/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import io.camunda.zeebe.broker.system.configuration.FlowControlCfg;
import io.camunda.zeebe.gateway.admin.BrokerAdminRequest;
import io.camunda.zeebe.shared.management.FlowControlEndpoint.FlowControlService;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.agrona.collections.IntHashSet;
import org.json.JSONArray;
import org.json.JSONObject;
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
  public CompletableFuture<JSONObject> get() {
    LOG.info("Fetching flow control configuration.");
    final var topology = client.getTopologyManager().getTopology();
    final JSONObject jsonObject = new JSONObject();
    final var futures =
        topology.getPartitions().stream()
            .map(partition -> fetchFlowConfigOnPartition(topology, partition))
            .toList();
    final JSONArray partitionsCfg = new JSONArray();
    return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
        .thenApply(
            element -> {
              futures.forEach(
                  future -> {
                    final JSONArray partitions = future.join();
                    partitions.forEach(partitionsCfg::put);
                  });
              jsonObject.put("partitions", partitionsCfg);
              return jsonObject;
            });
  }

  @Override
  public CompletableFuture<String> set(final FlowControlCfg flowControlCfg) {
    LOG.info("Setting flow control configuration.");

    final ObjectMapper objectMapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();
    final byte[] configuration;
    try {
      configuration = objectMapper.writeValueAsBytes(flowControlCfg);
    } catch (final JsonProcessingException e) {
      return CompletableFuture.completedFuture(
          "Failed to parse flow control configuration: " + e.getMessage());
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
    return CompletableFuture.allOf(results).thenApply(e -> "Flow control configuration set.");
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

  private CompletableFuture<JSONArray> fetchFlowConfigOnPartition(
      final BrokerClusterState topology, final Integer partitionId) {

    final var members = getMembers(topology, partitionId);
    final JSONArray partitions = new org.json.JSONArray();
    final JSONObject broker = new JSONObject();
    final List<CompletableFuture<JSONObject>> partitionsFutures =
        members.stream()
            .map(
                brokerId -> {
                  final var request = new BrokerAdminRequest();
                  request.setBrokerId(brokerId);
                  request.setPartitionId(partitionId);
                  request.getFLowControlConfiguration();
                  return client
                      .sendRequest(request)
                      .thenApply(
                          e -> {
                            final byte[] payload = e.getResponse().getPayload();
                            partitions.put(parsePayload(partitionId, payload));
                            return null;
                          })
                      .thenApply(partitionsArray -> broker.put("partitions", partitions));
                })
            .toList();

    return CompletableFuture.allOf(partitionsFutures.toArray(new CompletableFuture[0]))
        .thenApply(e -> partitions);
  }

  private IntHashSet getMembers(final BrokerClusterState topology, final Integer partitionId) {
    final var leader = topology.getLeaderForPartition(partitionId);
    final var followers =
        Optional.ofNullable(topology.getFollowersForPartition(partitionId)).orElseGet(Set::of);
    final var inactive =
        Optional.ofNullable(topology.getInactiveNodesForPartition(partitionId)).orElseGet(Set::of);

    final var members = new IntHashSet(topology.getReplicationFactor());
    members.add(leader);
    members.addAll(followers);
    members.addAll(inactive);
    return members;
  }

  private JSONObject parsePayload(final int partitionId, final byte[] payload) {
    final JSONObject partition = new JSONObject();
    partition.put("flowControlConfig", new String(payload));
    partition.put("partitionId", partitionId);
    return partition;
  }
}
