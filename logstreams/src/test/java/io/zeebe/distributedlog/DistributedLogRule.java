/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.distributedlog;

import static io.zeebe.protocol.Protocol.START_PARTITION_ID;

import io.atomix.cluster.Node;
import io.atomix.cluster.discovery.BootstrapDiscoveryProvider;
import io.atomix.core.Atomix;
import io.atomix.core.AtomixBuilder;
import io.atomix.protocols.backup.partition.PrimaryBackupPartitionGroup;
import io.atomix.protocols.raft.MultiRaftProtocol;
import io.atomix.protocols.raft.partition.RaftPartitionGroup;
import io.atomix.utils.net.Address;
import io.zeebe.distributedlog.impl.DistributedLogstreamConfig;
import io.zeebe.distributedlog.impl.DistributedLogstreamName;
import io.zeebe.distributedlog.impl.LogstreamConfig;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.servicecontainer.testing.ServiceContainerRule;
import io.zeebe.transport.SocketAddress;
import io.zeebe.transport.impl.util.SocketUtil;
import io.zeebe.util.FileUtil;
import io.zeebe.util.sched.ActorScheduler;
import io.zeebe.util.sched.future.ActorFuture;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DistributedLogRule extends ExternalResource {
  private final ActorScheduler actorScheduler;
  private final ServiceContainer serviceContainer;
  private final int nodeId;
  private final SocketAddress socketAddress;
  private final List<Node> otherNodes;
  private Atomix atomix;
  private final int numPartitions;
  private final int replicationFactor;
  private final List<String> members;

  private CompletableFuture<Void> nodeStarted;
  public static final Logger LOG = LoggerFactory.getLogger("io.zeebe.distributedlog.test");

  private final Map<Integer, DistributedLogPartitionRule> partitions = new HashMap<>();

  private Path rootDirectory;

  public static final ServiceName<Atomix> ATOMIX_SERVICE_NAME =
      ServiceName.newServiceName("cluster.base.atomix", Atomix.class);
  private final ActorFuture<Void> configFuture;

  public DistributedLogRule(
      ServiceContainerRule serviceContainerRule,
      final int nodeId,
      int numPartitions,
      int replicationFactor,
      List<String> members,
      List<Node> otherNodes) {
    this.actorScheduler = serviceContainerRule.getActorScheduler();
    this.serviceContainer = serviceContainerRule.get();
    this.nodeId = nodeId;
    this.numPartitions = numPartitions;
    this.replicationFactor = replicationFactor;
    this.socketAddress = SocketUtil.getNextAddress();
    this.members = members;
    this.otherNodes = otherNodes;
    try {
      rootDirectory = Files.createTempDirectory("dl-test-" + nodeId + "-");
    } catch (Exception e) {
    }
    final String memberId = String.valueOf(nodeId);

    final StorageConfigurationManager config =
        new StorageConfigurationManager(
            Collections.singletonList(rootDirectory.toAbsolutePath().toString()), "512M", "4M");

    LogstreamConfig.putConfig(memberId, config);
    LogstreamConfig.putServiceContainer(memberId, serviceContainer);
    LogstreamConfig.putRestoreClientFactory(
        memberId, p -> null); // return null until we figure out what to use to test

    configFuture = actorScheduler.submitActor(config);
  }

  public Node getNode() {
    return Node.builder()
        .withAddress(new Address(socketAddress.host(), socketAddress.port()))
        .build();
  }

  @Override
  protected void before() throws IOException {
    startNode();
  }

  public void startNode() throws IOException {
    nodeStarted =
        createAtomixNode()
            .whenComplete(
                (r, t) -> {
                  try {
                    createPartitions();
                  } catch (IOException e) {
                    e.printStackTrace();
                  }
                });
  }

  @Override
  protected void after() {
    stopNode();
    try {
      FileUtil.deleteFolder(rootDirectory.toAbsolutePath().toString());
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public void stopNode() {
    partitions.forEach((i, p) -> p.close());
    stopAtomixNode();
    nodeStarted = null;
  }

  private void stopAtomixNode() {
    atomix.stop().join();
    serviceContainer.removeService(ATOMIX_SERVICE_NAME);
  }

  private void createPartitions() throws IOException {
    // Create distributed log primitive so that logstreams are created
    final MultiRaftProtocol protocol =
        MultiRaftProtocol.builder()
            // Maps partitionName to partitionId
            .withPartitioner(DistributedLogstreamName.getInstance())
            .build();

    atomix
        .<DistributedLogstreamBuilder, DistributedLogstreamConfig, DistributedLogstream>
            primitiveBuilder("distributed-log", DistributedLogstreamType.instance())
        .withProtocol(protocol)
        .buildAsync()
        .join();

    for (int i = START_PARTITION_ID; i < START_PARTITION_ID + numPartitions; i++) {
      final DistributedLogPartitionRule partition =
          new DistributedLogPartitionRule(serviceContainer, nodeId, i, rootDirectory);
      partitions.put(i, partition);
      partition.start();
    }
  }

  private CompletableFuture<Void> createAtomixNode() throws IOException {

    configFuture.join();
    final AtomixBuilder atomixBuilder =
        Atomix.builder()
            .withClusterId("dl-test")
            .withMemberId(String.valueOf(nodeId))
            .withAddress(Address.from(socketAddress.host(), socketAddress.port()));
    if (otherNodes != null) {
      atomixBuilder.withMembershipProvider(
          BootstrapDiscoveryProvider.builder().withNodes(otherNodes).build());
    }

    final PrimaryBackupPartitionGroup systemGroup =
        PrimaryBackupPartitionGroup.builder("system").withNumPartitions(1).build();

    final String raftPartitionGroupName = "raft-atomix";

    final File raftDirectory = new File(rootDirectory.toString(), raftPartitionGroupName);
    if (!raftDirectory.exists()) {
      Files.createDirectory(raftDirectory.toPath());
    }

    final RaftPartitionGroup partitionGroup =
        RaftPartitionGroup.builder(raftPartitionGroupName)
            .withNumPartitions(numPartitions)
            .withPartitionSize(replicationFactor)
            .withMembers(members)
            .withDataDirectory(raftDirectory)
            .withFlushOnCommit()
            .build();

    atomixBuilder.withManagementGroup(systemGroup).withPartitionGroups(partitionGroup);

    atomix = atomixBuilder.build();

    serviceContainer.createService(ATOMIX_SERVICE_NAME, () -> atomix).install();

    return atomix.start();
  }

  public void becomeLeader(int partitionId) {
    partitions.get(partitionId).becomeLeader();
  }

  public void becomeFollower(int partitionId) {
    partitions.get(partitionId).becomeFollower();
  }

  public long writeEvent(int partitionId, final String message) {
    return partitions.get(partitionId).writeEvent(message);
  }

  protected void waitUntilNodesJoined()
      throws ExecutionException, InterruptedException, TimeoutException {
    LOG.info("Waiting for node {} start", this.nodeId);
    nodeStarted.get(50, TimeUnit.SECONDS);
    LOG.info("Node {} started", this.nodeId);
  }

  public boolean eventAppended(int partitionId, String message, long writePosition) {
    return partitions.get(partitionId).eventAppended(message, writePosition);
  }

  public int getCommittedEventsCount(int partitionId) {
    return partitions.get(partitionId).getCommittedEventsCount();
  }
}
