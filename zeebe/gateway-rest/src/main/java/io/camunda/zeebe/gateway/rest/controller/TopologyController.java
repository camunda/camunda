/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.atomix.utils.net.Address;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import io.camunda.zeebe.gateway.protocol.rest.BrokerInfo;
import io.camunda.zeebe.gateway.protocol.rest.Partition;
import io.camunda.zeebe.gateway.protocol.rest.Partition.HealthEnum;
import io.camunda.zeebe.gateway.protocol.rest.Partition.RoleEnum;
import io.camunda.zeebe.gateway.protocol.rest.TopologyResponse;
import io.camunda.zeebe.gateway.rest.Loggers;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.util.VersionUtil;
import java.util.Set;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping(path = {"/v1", "/v2"})
public final class TopologyController {

  private final BrokerClient client;

  public TopologyController(final BrokerClient client) {
    this.client = client;
  }

  @CamundaGetMapping(path = "/topology")
  public TopologyResponse get() {

    final var response = new TopologyResponse();
    final BrokerClusterState topology = client.getTopologyManager().getTopology();

    final String gatewayVersion = VersionUtil.getVersion();
    if (gatewayVersion != null && !gatewayVersion.isBlank()) {
      response.gatewayVersion(gatewayVersion);
    }

    if (topology != null) {
      response
          .clusterSize(topology.getClusterSize())
          .partitionsCount(topology.getPartitionsCount())
          .replicationFactor(topology.getReplicationFactor())
          .lastCompletedChangeId(String.valueOf(topology.getLastCompletedChangeId()));

      topology
          .getBrokers()
          .forEach(
              brokerId -> {
                final BrokerInfo brokerInfo = new BrokerInfo();
                addBrokerInfo(brokerInfo, brokerId, topology);
                addPartitionInfoToBrokerInfo(brokerInfo, brokerId, topology);

                response.addBrokersItem(brokerInfo);
              });
    }

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
              final var isRoleSet = setRole(brokerId, partitionId, topology, partition);
              if (!isRoleSet) {
                return;
              }

              final var status = topology.getPartitionHealth(brokerId, partitionId);
              switch (status) {
                case HEALTHY -> partition.setHealth(HealthEnum.HEALTHY);
                case UNHEALTHY -> partition.setHealth(HealthEnum.UNHEALTHY);
                case DEAD -> partition.setHealth(HealthEnum.DEAD);
                default ->
                    Loggers.REST_LOGGER.debug(
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
