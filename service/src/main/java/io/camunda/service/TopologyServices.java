/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.atomix.primitive.partition.PartitionId;
import io.atomix.utils.net.Address;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.TopologyServices.Topology.Builder;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import io.camunda.zeebe.protocol.record.PartitionHealthStatus;
import io.camunda.zeebe.util.VersionUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TopologyServices extends ApiServices<TopologyServices> {

  private static final Logger LOGGER = LoggerFactory.getLogger(TopologyServices.class);

  public TopologyServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final CamundaAuthentication authentication,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    super(
        brokerClient,
        securityContextProvider,
        authentication,
        executorProvider,
        brokerRequestAuthorizationConverter);
  }

  @Override
  public TopologyServices withAuthentication(final CamundaAuthentication authentication) {
    return new TopologyServices(
        brokerClient,
        securityContextProvider,
        authentication,
        executorProvider,
        brokerRequestAuthorizationConverter);
  }

  public CompletableFuture<ClusterStatus> getStatus() {
    try {
      if (!hasAPartitionWithAHealthyLeader()) {
        return CompletableFuture.completedFuture(ClusterStatus.UNHEALTHY);
      }

      return CompletableFuture.completedFuture(ClusterStatus.HEALTHY);
    } catch (final Exception ex) {
      return CompletableFuture.failedFuture(ErrorMapper.mapError(ex));
    }
  }

  private boolean hasAPartitionWithAHealthyLeader() {
    final var topology = brokerClient.getTopologyManager().getTopology();

    if (topology == null) {
      return false;
    }

    final var partitions = topology.getPartitions();

    return partitions.stream()
        .anyMatch(
            partition -> {
              final var leader =
                  topology.getLeaderForPartition(new PartitionId("raft-partition", partition));
              return topology.getPartitionHealth(
                      leader, new PartitionId("raft-partition", partition))
                  == PartitionHealthStatus.HEALTHY;
            });
  }

  public CompletableFuture<Topology> getTopology() {
    try {
      final var topology = Topology.Builder.create();
      final var clusterState = brokerClient.getTopologyManager().getTopology();

      final var gatewayVersion = VersionUtil.getVersion();
      if (gatewayVersion != null && !gatewayVersion.isBlank()) {
        topology.gatewayVersion(gatewayVersion);
      }

      if (clusterState != null) {
        topology
            .clusterId(clusterState.getClusterId())
            .clusterSize(clusterState.getClusterSize())
            .partitionsCount(clusterState.getPartitionsCount())
            .replicationFactor(clusterState.getReplicationFactor())
            .lastCompletedChangeId(clusterState.getLastCompletedChangeId());

        clusterState
            .getBrokers()
            .forEach(
                brokerId -> {
                  final var broker = Broker.Builder.create();
                  addBrokerInfo(broker, brokerId, clusterState);
                  addPartitionInfoToBrokerInfo(broker, brokerId, clusterState);

                  topology.addBroker(broker.build());
                });
      }

      return CompletableFuture.completedFuture(topology.build());
    } catch (final Exception ex) {
      return CompletableFuture.failedFuture(ErrorMapper.mapError(ex));
    }
  }

  private void addBrokerInfo(
      final Broker.Builder broker, final Integer brokerId, final BrokerClusterState topology) {
    final var brokerAddress = topology.getBrokerAddress(brokerId);
    final var address = Address.from(brokerAddress);

    broker
        .nodeId(brokerId)
        .host(address.host())
        .port(address.port())
        .version(topology.getBrokerVersion(brokerId));
  }

  private void addPartitionInfoToBrokerInfo(
      final Broker.Builder broker, final Integer brokerId, final BrokerClusterState topology) {
    topology
        .getPartitions()
        .forEach(
            partitionId -> {
              final var partition = Partition.Builder.create();

              partition.partitionId(partitionId);
              final var isRoleSet = setRole(brokerId, partitionId, topology, partition);
              if (!isRoleSet) {
                return;
              }

              final var status =
                  topology.getPartitionHealth(
                      brokerId, new PartitionId("raft-partition", partitionId));
              switch (status) {
                case HEALTHY -> partition.health(Health.HEALTHY);
                case UNHEALTHY -> partition.health(Health.UNHEALTHY);
                case DEAD -> partition.health(Health.DEAD);
                default ->
                    LOGGER.debug("Unsupported partition broker health status '{}'", status.name());
              }
              broker.addPartition(partition.build());
            });
  }

  private boolean setRole(
      final Integer brokerId,
      final Integer partitionId,
      final BrokerClusterState topology,
      final Partition.Builder partition) {
    final var partitionLeader =
        topology.getLeaderForPartition(new PartitionId("raft-partition", partitionId));
    final var partitionFollowers =
        topology.getFollowersForPartition(new PartitionId("raft-partition", partitionId));
    final var partitionInactives =
        topology.getInactiveNodesForPartition(new PartitionId("raft-partition", partitionId));

    if (partitionLeader == brokerId) {
      partition.role(Role.LEADER);
    } else if (partitionFollowers != null && partitionFollowers.contains(brokerId)) {
      partition.role(Role.FOLLOWER);
    } else if (partitionInactives != null && partitionInactives.contains(brokerId)) {
      partition.role(Role.INACTIVE);
    } else {
      return false;
    }

    return true;
  }

  public record Topology(
      List<Broker> brokers,
      String clusterId,
      Integer clusterSize,
      Integer partitionsCount,
      Integer replicationFactor,
      String gatewayVersion,
      Long lastCompletedChangeId) {

    static class Builder {
      List<Broker> brokers = new ArrayList<>();
      String clusterId;
      Integer clusterSize;
      Integer partitionsCount;
      Integer replicationFactor;
      String gatewayVersion;
      Long lastCompletedChangeId;

      public static Builder create() {
        return new Builder();
      }

      public Builder addBroker(final Broker broker) {
        brokers.add(broker);
        return this;
      }

      public Builder clusterId(final String clusterId) {
        this.clusterId = clusterId;
        return this;
      }

      public Builder clusterSize(final Integer clusterSize) {
        this.clusterSize = clusterSize;
        return this;
      }

      public Builder partitionsCount(final Integer partitionsCount) {
        this.partitionsCount = partitionsCount;
        return this;
      }

      public Builder replicationFactor(final Integer replicationFactor) {
        this.replicationFactor = replicationFactor;
        return this;
      }

      public Builder gatewayVersion(final String gatewayVersion) {
        this.gatewayVersion = gatewayVersion;
        return this;
      }

      public Builder lastCompletedChangeId(final long lastCompletedChangeId) {
        this.lastCompletedChangeId = lastCompletedChangeId;
        return this;
      }

      public Topology build() {
        return new Topology(
            brokers,
            clusterId,
            clusterSize,
            partitionsCount,
            replicationFactor,
            gatewayVersion,
            lastCompletedChangeId);
      }
    }
  }

  public record Broker(
      Integer nodeId, String host, Integer port, List<Partition> partitions, String version) {

    static class Builder {
      Integer nodeId;
      String host;
      Integer port;
      List<Partition> partitions = new ArrayList<>();
      String version;

      public static Builder create() {
        return new Builder();
      }

      public Builder nodeId(final Integer nodeId) {
        this.nodeId = nodeId;
        return this;
      }

      public Builder host(final String host) {
        this.host = host;
        return this;
      }

      public Builder port(final Integer port) {
        this.port = port;
        return this;
      }

      public Builder addPartition(final Partition partition) {
        partitions.add(partition);
        return this;
      }

      public Builder version(final String version) {
        this.version = version;
        return this;
      }

      public Broker build() {
        return new Broker(nodeId, host, port, partitions, version);
      }
    }
  }

  public record Partition(Integer partitionId, Role role, Health health) {
    static class Builder {
      Integer partitionId;
      Role role;
      Health health;

      public static Builder create() {
        return new Builder();
      }

      public Builder partitionId(final Integer partitionId) {
        this.partitionId = partitionId;
        return this;
      }

      public Builder role(final Role role) {
        this.role = role;
        return this;
      }

      public Builder health(final Health health) {
        this.health = health;
        return this;
      }

      public Partition build() {
        return new Partition(partitionId, role, health);
      }
    }
  }

  /** Describes the Raft role of the broker for a given partition. */
  public enum Role {
    LEADER,
    FOLLOWER,
    INACTIVE
  }

  /** Describes the current health of the partition. */
  public enum Health {
    HEALTHY,
    UNHEALTHY,
    DEAD
  }

  public enum ClusterStatus {
    HEALTHY,
    UNHEALTHY
  }
}
