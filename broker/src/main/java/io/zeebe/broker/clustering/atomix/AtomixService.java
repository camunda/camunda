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
import io.atomix.core.Atomix;
import io.atomix.core.AtomixBuilder;
import io.atomix.protocols.raft.partition.RaftPartitionGroup;
import io.atomix.protocols.raft.partition.RaftPartitionGroup.Builder;
import io.atomix.utils.net.Address;
import io.zeebe.broker.Loggers;
import io.zeebe.broker.clustering.atomix.storage.snapshot.DbSnapshotStoreFactory;
import io.zeebe.broker.clustering.base.partitions.Partition;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.broker.system.configuration.ClusterCfg;
import io.zeebe.broker.system.configuration.DataCfg;
import io.zeebe.broker.system.configuration.NetworkCfg;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.util.ByteValue;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.agrona.IoUtil;
import org.slf4j.Logger;

public class AtomixService implements Service<Atomix> {
  private static final Logger LOG = Loggers.CLUSTERING_LOGGER;

  private final BrokerCfg configuration;
  private Atomix atomix;

  public AtomixService(final BrokerCfg configuration) {
    this.configuration = configuration;
  }

  @Override
  public void start(final ServiceStartContext startContext) {
    final var clusterCfg = configuration.getCluster();
    final var nodeId = clusterCfg.getNodeId();
    final var localMemberId = Integer.toString(nodeId);
    final var networkCfg = configuration.getNetwork();

    final NodeDiscoveryProvider discoveryProvider =
        createDiscoveryProvider(clusterCfg, localMemberId);

    LOG.debug("Setup atomix node in cluster {}", clusterCfg.getClusterName());

    final AtomixBuilder atomixBuilder =
        Atomix.builder()
            .withClusterId(clusterCfg.getClusterName())
            .withMemberId(localMemberId)
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

    final String raftPartitionGroupName = Partition.GROUP_NAME;
    final RaftPartitionGroup partitionGroup =
        createRaftPartitionGroup(rootDirectory, raftPartitionGroupName);

    atomix =
        atomixBuilder.withManagementGroup(systemGroup).withPartitionGroups(partitionGroup).build();
  }

  @Override
  public void stop(final ServiceStopContext stopContext) {
    final CompletableFuture<Void> stopFuture = atomix.stop();
    stopContext.async(mapCompletableFuture(stopFuture));
  }

  @Override
  public Atomix get() {
    return atomix;
  }

  private RaftPartitionGroup createRaftPartitionGroup(
      final String rootDirectory, final String raftPartitionGroupName) {

    final File raftDirectory = new File(rootDirectory, raftPartitionGroupName);
    IoUtil.ensureDirectoryExists(raftDirectory, "Raft data directory");

    final ClusterCfg clusterCfg = configuration.getCluster();
    final DataCfg dataCfg = configuration.getData();
    final NetworkCfg networkCfg = configuration.getNetwork();

    final Builder partitionGroupBuilder =
        RaftPartitionGroup.builder(raftPartitionGroupName)
            .withNumPartitions(clusterCfg.getPartitionsCount())
            .withPartitionSize(clusterCfg.getReplicationFactor())
            .withMembers(getRaftGroupMembers(clusterCfg))
            .withDataDirectory(raftDirectory)
            .withStateMachineFactory(ZeebeRaftStateMachine::new)
            .withSnapshotStoreFactory(new DbSnapshotStoreFactory(configuration.getData().getMaxSnapshots()))
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

  private List<String> getRaftGroupMembers(final ClusterCfg clusterCfg) {
    final int clusterSize = clusterCfg.getClusterSize();
    // node ids are always 0 to clusterSize - 1
    final List<String> members = new ArrayList<>();
    for (int i = 0; i < clusterSize; i++) {
      members.add(Integer.toString(i));
    }
    return members;
  }

  private NodeDiscoveryProvider createDiscoveryProvider(
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

  private ActorFuture<Void> mapCompletableFuture(final CompletableFuture<Void> atomixFuture) {
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
