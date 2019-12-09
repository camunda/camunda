/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.clustering.atomix;

import io.atomix.cluster.Node;
import io.atomix.cluster.discovery.BootstrapDiscoveryBuilder;
import io.atomix.cluster.discovery.BootstrapDiscoveryProvider;
import io.atomix.cluster.discovery.NodeDiscoveryProvider;
import io.atomix.cluster.protocol.GroupMembershipProtocol;
import io.atomix.cluster.protocol.SwimMembershipProtocol;
import io.atomix.core.Atomix;
import io.atomix.core.AtomixBuilder;
import io.atomix.protocols.raft.partition.RaftPartitionGroup;
import io.atomix.protocols.raft.partition.RaftPartitionGroup.Builder;
import io.atomix.utils.net.Address;
import io.zeebe.broker.Loggers;
import io.zeebe.broker.clustering.atomix.storage.snapshot.DbSnapshotStoreFactory;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.broker.system.configuration.ClusterCfg;
import io.zeebe.broker.system.configuration.DataCfg;
import io.zeebe.broker.system.configuration.NetworkCfg;
import io.zeebe.util.ByteValue;
import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.agrona.IoUtil;
import org.slf4j.Logger;

public final class AtomixFactory {
  public static final String GROUP_NAME = "raft-partition";

  private static final Logger LOG = Loggers.CLUSTERING_LOGGER;

  private AtomixFactory() {}

  public static Atomix fromConfiguration(BrokerCfg configuration) {
    final var clusterCfg = configuration.getCluster();
    final var nodeId = clusterCfg.getNodeId();
    final var localMemberId = Integer.toString(nodeId);
    final var networkCfg = configuration.getNetwork();

    final NodeDiscoveryProvider discoveryProvider =
        createDiscoveryProvider(clusterCfg, localMemberId);

    final GroupMembershipProtocol membershipProtocol =
        SwimMembershipProtocol.builder()
            .withFailureTimeout(Duration.ofMillis(clusterCfg.getGossipFailureTimeout()))
            .withGossipInterval(Duration.ofMillis(clusterCfg.getGossipInterval()))
            .withProbeInterval(Duration.ofMillis(clusterCfg.getGossipProbeInterval()))
            .build();

    final AtomixBuilder atomixBuilder =
        Atomix.builder()
            .withClusterId(clusterCfg.getClusterName())
            .withMemberId(localMemberId)
            .withMembershipProtocol(membershipProtocol)
            .withAddress(
                Address.from(
                    networkCfg.getInternalApi().getAdvertisedHost(),
                    networkCfg.getInternalApi().getAdvertisedPort()))
            .withMessagingPort(networkCfg.getInternalApi().getPort())
            .withMessagingInterface(networkCfg.getInternalApi().getHost())
            .withMembershipProvider(discoveryProvider);

    final DataCfg dataConfiguration = configuration.getData();
    final String rootDirectory = dataConfiguration.getDirectories().get(0);
    IoUtil.ensureDirectoryExists(new File(rootDirectory), "Zeebe data directory");

    final String systemPartitionName = "system";
    final File systemDirectory = new File(rootDirectory, systemPartitionName);
    IoUtil.ensureDirectoryExists(systemDirectory, "Raft system directory");

    final RaftPartitionGroup systemGroup =
        RaftPartitionGroup.builder(systemPartitionName)
            .withNumPartitions(1)
            .withPartitionSize(clusterCfg.getClusterSize())
            .withMembers(getRaftGroupMembers(clusterCfg))
            .withDataDirectory(systemDirectory)
            .withFlushOnCommit()
            .build();

    final RaftPartitionGroup partitionGroup =
        createRaftPartitionGroup(configuration, rootDirectory);

    return atomixBuilder
        .withManagementGroup(systemGroup)
        .withPartitionGroups(partitionGroup)
        .build();
  }

  private static RaftPartitionGroup createRaftPartitionGroup(
      final BrokerCfg configuration, final String rootDirectory) {

    final File raftDirectory = new File(rootDirectory, AtomixFactory.GROUP_NAME);
    IoUtil.ensureDirectoryExists(raftDirectory, "Raft data directory");

    final ClusterCfg clusterCfg = configuration.getCluster();
    final DataCfg dataCfg = configuration.getData();
    final NetworkCfg networkCfg = configuration.getNetwork();

    final Builder partitionGroupBuilder =
        RaftPartitionGroup.builder(AtomixFactory.GROUP_NAME)
            .withNumPartitions(clusterCfg.getPartitionsCount())
            .withPartitionSize(clusterCfg.getReplicationFactor())
            .withMembers(getRaftGroupMembers(clusterCfg))
            .withDataDirectory(raftDirectory)
            .withStateMachineFactory(ZeebeRaftStateMachine::new)
            .withSnapshotStoreFactory(new DbSnapshotStoreFactory())
            .withFlushOnCommit();

    // by default, the Atomix max entry size is 1 MB
    final ByteValue maxMessageSize = networkCfg.getMaxMessageSize();
    partitionGroupBuilder.withMaxEntrySize((int) maxMessageSize.toBytes());

    Optional.ofNullable(dataCfg.getRaftSegmentSize())
        .map(ByteValue::new)
        .ifPresent(
            segmentSize -> {
              if (segmentSize.toBytes() < maxMessageSize.toBytes()) {
                throw new IllegalArgumentException(
                    String.format(
                        "Expected the raft segment size greater than the max message size of %s, but was %s.",
                        maxMessageSize, segmentSize));
              }

              partitionGroupBuilder.withSegmentSize(segmentSize.toBytes());
            });

    return partitionGroupBuilder.build();
  }

  private static List<String> getRaftGroupMembers(final ClusterCfg clusterCfg) {
    final int clusterSize = clusterCfg.getClusterSize();
    // node ids are always 0 to clusterSize - 1
    final List<String> members = new ArrayList<>();
    for (int i = 0; i < clusterSize; i++) {
      members.add(Integer.toString(i));
    }
    return members;
  }

  private static NodeDiscoveryProvider createDiscoveryProvider(
      final ClusterCfg clusterCfg, final String localMemberId) {
    final BootstrapDiscoveryBuilder builder = BootstrapDiscoveryProvider.builder();
    final List<String> initialContactPoints = clusterCfg.getInitialContactPoints();

    final List<Node> nodes = new ArrayList<>();
    initialContactPoints.forEach(
        contactAddress -> {
          final String[] address = contactAddress.split(":");
          final int memberPort = Integer.parseInt(address[1]);

          final Node node =
              Node.builder().withAddress(Address.from(address[0], memberPort)).build();
          LOG.debug("Member {} will contact node: {}", localMemberId, node.address());
          nodes.add(node);
        });
    return builder.withNodes(nodes).build();
  }
}
