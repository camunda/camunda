/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.clustering.base.gossip;

import io.atomix.cluster.Node;
import io.atomix.cluster.discovery.BootstrapDiscoveryBuilder;
import io.atomix.cluster.discovery.BootstrapDiscoveryProvider;
import io.atomix.cluster.discovery.NodeDiscoveryProvider;
import io.atomix.core.Atomix;
import io.atomix.core.AtomixBuilder;
import io.atomix.protocols.raft.partition.RaftPartitionGroup;
import io.atomix.protocols.raft.partition.RaftPartitionGroup.Builder;
import io.atomix.utils.net.Address;
import io.zeebe.broker.Loggers;
import io.zeebe.broker.clustering.base.partitions.Partition;
import io.zeebe.broker.logstreams.restore.BrokerRestoreFactory;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.broker.system.configuration.ClusterCfg;
import io.zeebe.broker.system.configuration.DataCfg;
import io.zeebe.broker.system.configuration.NetworkCfg;
import io.zeebe.distributedlog.impl.LogstreamConfig;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.util.ByteValue;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;

public class AtomixService implements Service<Atomix> {

  private static final Logger LOG = Loggers.CLUSTERING_LOGGER;

  private final BrokerCfg configuration;
  private Atomix atomix;

  public AtomixService(final BrokerCfg configuration) {
    this.configuration = configuration;
  }

  @Override
  public void start(ServiceStartContext startContext) {
    final ClusterCfg clusterCfg = configuration.getCluster();

    final int nodeId = clusterCfg.getNodeId();
    final String localMemberId = Integer.toString(nodeId);

    final NetworkCfg networkCfg = configuration.getNetwork();
    final String host = networkCfg.getInternalApi().getHost();
    final int port = networkCfg.getInternalApi().getPort();

    final NodeDiscoveryProvider discoveryProvider =
        createDiscoveryProvider(clusterCfg, localMemberId);

    LOG.debug("Setup atomix node in cluster {}", clusterCfg.getClusterName());

    final AtomixBuilder atomixBuilder =
        Atomix.builder()
            .withClusterId(clusterCfg.getClusterName())
            .withMemberId(localMemberId)
            .withAddress(Address.from(host, port))
            .withMembershipProvider(discoveryProvider);

    final DataCfg dataConfiguration = configuration.getData();
    final String rootDirectory = dataConfiguration.getDirectories().get(0);

    final String systemPartitionName = "system";
    final File systemDirectory = new File(rootDirectory, systemPartitionName);
    if (!systemDirectory.exists()) {
      try {
        Files.createDirectory(systemDirectory.toPath());
      } catch (final IOException e) {
        throw new RuntimeException("Unable to create directory " + systemDirectory, e);
      }
    }

    final RaftPartitionGroup systemGroup =
        RaftPartitionGroup.builder(systemPartitionName)
            .withNumPartitions(1)
            .withPartitionSize(configuration.getCluster().getClusterSize())
            .withMembers(getRaftGroupMembers(clusterCfg))
            .withDataDirectory(systemDirectory)
            .withFlushOnCommit()
            .build();

    final String raftPartitionGroupName = Partition.GROUP_NAME;

    final File raftDirectory = new File(rootDirectory, raftPartitionGroupName);
    if (!raftDirectory.exists()) {
      try {
        Files.createDirectory(raftDirectory.toPath());
      } catch (final IOException e) {
        throw new RuntimeException("Unable to create directory " + raftDirectory, e);
      }
    }

    final Builder partitionGroupBuilder =
        RaftPartitionGroup.builder(raftPartitionGroupName)
            .withNumPartitions(configuration.getCluster().getPartitionsCount())
            .withPartitionSize(configuration.getCluster().getReplicationFactor())
            .withMembers(getRaftGroupMembers(clusterCfg))
            .withDataDirectory(raftDirectory)
            .withFlushOnCommit();

    if (dataConfiguration.getRaftSegmentSize() != null) {
      partitionGroupBuilder.withSegmentSize(
          new ByteValue(dataConfiguration.getRaftSegmentSize()).toBytes());
    }

    final RaftPartitionGroup partitionGroup = partitionGroupBuilder.build();

    atomixBuilder.withManagementGroup(systemGroup).withPartitionGroups(partitionGroup);

    atomix = atomixBuilder.build();

    final BrokerRestoreFactory restoreFactory =
        new BrokerRestoreFactory(
            atomix.getCommunicationService(),
            atomix.getPartitionService(),
            raftPartitionGroupName,
            localMemberId);
    LogstreamConfig.putRestoreFactory(localMemberId, restoreFactory);
  }

  @Override
  public void stop(ServiceStopContext stopContext) {
    final String localMemberId = atomix.getMembershipService().getLocalMember().id().id();
    final CompletableFuture<Void> stopFuture = atomix.stop();
    stopContext.async(mapCompletableFuture(stopFuture));
    LogstreamConfig.removeRestoreFactory(localMemberId);
  }

  @Override
  public Atomix get() {
    return atomix;
  }

  private List<String> getRaftGroupMembers(ClusterCfg clusterCfg) {
    final int clusterSize = clusterCfg.getClusterSize();
    // node ids are always 0 to clusterSize - 1
    final List<String> members = new ArrayList<>();
    for (int i = 0; i < clusterSize; i++) {
      members.add(Integer.toString(i));
    }
    return members;
  }

  private NodeDiscoveryProvider createDiscoveryProvider(
      ClusterCfg clusterCfg, String localMemberId) {
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

  private ActorFuture<Void> mapCompletableFuture(CompletableFuture<Void> atomixFuture) {
    final ActorFuture<Void> mappedActorFuture = new CompletableActorFuture<>();

    atomixFuture
        .thenAccept(mappedActorFuture::complete)
        .exceptionally(
            t -> {
              mappedActorFuture.completeExceptionally(t);
              return null;
            });
    return mappedActorFuture;
  }
}
