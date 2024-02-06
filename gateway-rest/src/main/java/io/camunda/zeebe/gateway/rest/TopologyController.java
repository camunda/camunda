/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.rest;

import io.atomix.utils.net.Address;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import io.camunda.zeebe.gateway.protocol.rest.BrokerInfo;
import io.camunda.zeebe.gateway.protocol.rest.Partition;
import io.camunda.zeebe.gateway.protocol.rest.Partition.HealthEnum;
import io.camunda.zeebe.gateway.protocol.rest.Partition.RoleEnum;
import io.camunda.zeebe.gateway.protocol.rest.TopologyResponse;
import io.camunda.zeebe.util.VersionUtil;
import java.util.ArrayList;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;

@ZeebeRestController
public final class TopologyController {
  private final BrokerClient client;

  @Autowired
  public TopologyController(final BrokerClient client) {
    this.client = client;
  }

  @GetMapping(path = "/topology", produces = "application/json")
  public TopologyResponse get() {

    final TopologyResponse response = new TopologyResponse();
    final BrokerClusterState topology = client.getTopologyManager().getTopology();

    final String gatewayVersion = VersionUtil.getVersion();
    if (gatewayVersion != null && !gatewayVersion.isBlank()) {
      response.setGatewayVersion(gatewayVersion);
    }

    final ArrayList<BrokerInfo> brokers = new ArrayList<>();
    if (topology != null) {
      response.setClusterSize(topology.getClusterSize());
      response.setPartitionsCount(topology.getPartitionsCount());
      response.setReplicationFactor(topology.getReplicationFactor());

      topology
          .getBrokers()
          .forEach(
              brokerId -> {
                final BrokerInfo brokerInfo = new BrokerInfo();
                addBrokerInfo(brokerInfo, brokerId, topology);
                addPartitionInfoToBrokerInfo(brokerInfo, brokerId, topology);

                brokers.add(brokerInfo);
              });
    }
    response.setBrokers(brokers);

    return response;
  }

  private void addBrokerInfo(
      final BrokerInfo brokerInfo, final Integer brokerId, final BrokerClusterState topology) {
    final String brokerAddress = topology.getBrokerAddress(brokerId);
    final Address address = Address.from(brokerAddress);

    brokerInfo.setNodeId(brokerId);
    brokerInfo.setHost(address.host());
    brokerInfo.setPort(address.port());
    brokerInfo.setVersion(topology.getBrokerVersion(brokerId));
  }

  private void addPartitionInfoToBrokerInfo(
      final BrokerInfo brokerInfo, final Integer brokerId, final BrokerClusterState topology) {
    topology
        .getPartitions()
        .forEach(
            partitionId -> {
              final Partition partition = new Partition();

              partition.setPartitionId(partitionId);

              if (!setRole(brokerId, partitionId, topology, partition)) {
                return;
              }

              final var status = topology.getPartitionHealth(brokerId, partitionId);
              switch (status) {
                case HEALTHY -> partition.setHealth(HealthEnum.HEALTHY);
                case UNHEALTHY -> partition.setHealth(HealthEnum.UNHEALTHY);
                case DEAD -> partition.setHealth(HealthEnum.DEAD);
                default -> Loggers.REST_LOGGER.debug(
                    "Unsupported partition broker health status '{}'", status.name());
              }
              brokerInfo.addPartitionsItem(partition);
            });
  }

  private boolean setRole(
      final Integer brokerId,
      final Integer partitionId,
      final BrokerClusterState topology,
      final Partition partition) {
    final int partitionLeader = topology.getLeaderForPartition(partitionId);
    final Set<Integer> partitionFollowers = topology.getFollowersForPartition(partitionId);
    final Set<Integer> partitionInactives = topology.getInactiveNodesForPartition(partitionId);

    if (partitionLeader == brokerId) {
      partition.setRole(RoleEnum.LEADER);
    } else if (partitionFollowers != null && partitionFollowers.contains(brokerId)) {
      partition.setRole(RoleEnum.FOLLOWER);
    } else if (partitionInactives != null && partitionInactives.contains(brokerId)) {
      partition.setRole(RoleEnum.INACTIVE);
    } else {
      return false;
    }

    return true;
  }
}
