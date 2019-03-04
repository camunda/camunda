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
import io.atomix.protocols.raft.partition.RaftPartitionGroup;
import io.atomix.utils.net.Address;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.servicecontainer.testing.ServiceContainerRule;
import io.zeebe.transport.SocketAddress;
import io.zeebe.transport.impl.util.SocketUtil;
import io.zeebe.util.sched.ActorScheduler;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
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
  private int numPartitions;
  private int replicationFactor;
  private List<String> members;
  private DistributedLogstream distributedLog;

  private CompletableFuture<Void> nodeStarted;
  public static final Logger LOG = LoggerFactory.getLogger("io.zeebe.distributedlog.test");

  private final Map<Integer, DistributedLogPartitionRule> partitions = new HashMap<>();

  public static final ServiceName<Atomix> ATOMIX_SERVICE_NAME =
      ServiceName.newServiceName("cluster.base.atomix", Atomix.class);

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
  }

  public Node getNode() {
    return Node.builder()
        .withAddress(new Address(socketAddress.host(), socketAddress.port()))
        .build();
  }

  @Override
  protected void before() throws IOException {
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
    partitions.forEach((i, p) -> p.close());
    stopAtomixNode();
  }

  private void stopAtomixNode() {
    atomix.stop();
  }

  private void createPartitions() throws IOException {
    for (int i = START_PARTITION_ID; i < START_PARTITION_ID + numPartitions; i++) {
      final DistributedLogPartitionRule partition =
          new DistributedLogPartitionRule(serviceContainer, nodeId, i);
      partitions.put(i, partition);
      partition.start();
    }
  }

  private CompletableFuture<Void> createAtomixNode() throws IOException {

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

    final String rootDirectory = Files.createTempDirectory("dl-test-" + nodeId + "-").toString();
    final File raftDirectory = new File(rootDirectory, raftPartitionGroupName);
    Files.createDirectory(raftDirectory.toPath());

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

    serviceContainer.createService(ATOMIX_SERVICE_NAME, () -> atomix).install().join();

    return atomix.start();
  }

  public void becomeLeader(int partitionId) {
    partitions.get(partitionId).becomeLeader();
  }

  public long writeEvent(int partitionId, final String message) {
    return partitions.get(partitionId).writeEvent(message);
  }

  protected void waitUntilNodesJoined()
      throws ExecutionException, InterruptedException, TimeoutException {
    nodeStarted.get(50, TimeUnit.SECONDS);
  }

  public boolean eventAppended(int partitionId, String message, long writePosition) {
    return partitions.get(partitionId).eventAppended(message, writePosition);
  }

  public int getCommittedEventsCount(int partitionId) {
    return partitions.get(partitionId).getCommittedEventsCount();
  }
}
