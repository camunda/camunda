/*
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
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
package io.atomix.raft.zeebe.util;

import io.atomix.cluster.AtomixCluster;
import io.atomix.cluster.Member;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.Node;
import io.atomix.cluster.discovery.BootstrapDiscoveryProvider;
import io.atomix.cluster.discovery.NodeDiscoveryProvider;
import io.atomix.primitive.partition.ManagedPartitionGroup;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.partition.impl.DefaultPartitionManagementService;
import io.atomix.raft.partition.RaftPartition;
import io.atomix.raft.partition.RaftPartitionGroup;
import io.atomix.raft.partition.RoundRobinPartitionDistributor;
import io.atomix.raft.partition.impl.RaftPartitionServer;
import io.atomix.raft.snapshot.TestSnapshotStore;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class ZeebeTestNode {

  public static final String CLUSTER_ID = "zeebe";
  private static final String DATA_PARTITION_GROUP_NAME = "data";
  private static final String HOST = "localhost";
  private static final int BASE_PORT = 10_000;
  private final Member member;
  private final Node node;
  private final File directory;

  private RaftPartitionGroup dataPartitionGroup;
  private AtomixCluster cluster;

  public ZeebeTestNode(final int id, final File directory) {
    final String textualId = String.valueOf(id);

    this.directory = directory;
    node = Node.builder().withId(textualId).withHost(HOST).withPort(BASE_PORT + id).build();
    member = Member.member(MemberId.from(textualId), node.address());
  }

  public RaftPartitionServer getPartitionServer(final int id) {
    return getPartition(id).getServer();
  }

  RaftPartition getPartition(final int id) {
    return (RaftPartition) getDataPartitionGroup().getPartition(String.valueOf(id));
  }

  private ManagedPartitionGroup getDataPartitionGroup() {
    return dataPartitionGroup;
  }

  public CompletableFuture<Void> start(final Collection<ZeebeTestNode> nodes) {
    cluster = buildCluster(nodes);
    dataPartitionGroup =
        buildPartitionGroup(RaftPartitionGroup.builder(DATA_PARTITION_GROUP_NAME), nodes).build();

    return cluster
        .start()
        .thenCompose(
            ignored ->
                CompletableFuture.allOf(
                    dataPartitionGroup
                        .join(
                            new DefaultPartitionManagementService(
                                cluster.getMembershipService(), cluster.getCommunicationService()))
                        .toArray(CompletableFuture[]::new)))
        .thenApply(v -> null);
  }

  private AtomixCluster buildCluster(final Collection<ZeebeTestNode> nodes) {
    return AtomixCluster.builder()
        .withAddress(node.address())
        .withClusterId(CLUSTER_ID)
        .withMembershipProvider(buildDiscoveryProvider(nodes))
        .withMemberId(getMemberId())
        .build();
  }

  public RaftPartitionGroup getPartitionGroup() {
    return dataPartitionGroup;
  }

  public MemberId getMemberId() {
    return member.id();
  }

  private NodeDiscoveryProvider buildDiscoveryProvider(final Collection<ZeebeTestNode> nodes) {
    return BootstrapDiscoveryProvider.builder()
        .withNodes(nodes.stream().map(ZeebeTestNode::getNode).collect(Collectors.toList()))
        .build();
  }

  public Node getNode() {
    return node;
  }

  private RaftPartitionGroup.Builder buildPartitionGroup(
      final RaftPartitionGroup.Builder builder, final Collection<ZeebeTestNode> nodes) {
    final Set<MemberId> members =
        nodes.stream().map(ZeebeTestNode::getMember).map(Member::id).collect(Collectors.toSet());
    members.add(member.id());

    final var partitionDistribution =
        new RoundRobinPartitionDistributor()
            .distributePartitions(members, List.of(PartitionId.from("test", 1)), members.size());
    return builder
        .withPartitionDistribution(partitionDistribution)
        .withDataDirectory(directory)
        .withSegmentSize(1024L)
        .withSnapshotStoreFactory(
            (path, partition) -> new TestSnapshotStore(new AtomicReference<>()));
  }

  public Member getMember() {
    return member;
  }

  public CompletableFuture<Void> stop() {
    return dataPartitionGroup.close().thenCompose(ignored -> cluster.stop());
  }

  public AtomixCluster getCluster() {
    return cluster;
  }

  @Override
  public String toString() {
    return "ZeebeTestNode{" + "member=" + member + '}';
  }
}
