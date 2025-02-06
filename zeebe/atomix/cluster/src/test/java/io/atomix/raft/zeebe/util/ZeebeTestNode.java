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
import io.atomix.cluster.NoopSnapshotStore;
import io.atomix.cluster.discovery.BootstrapDiscoveryProvider;
import io.atomix.cluster.discovery.NodeDiscoveryProvider;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.atomix.primitive.partition.impl.DefaultPartitionManagementService;
import io.atomix.raft.partition.RaftPartition;
import io.atomix.raft.partition.RaftPartitionConfig;
import io.atomix.raft.partition.RaftStorageConfig;
import io.atomix.raft.partition.impl.RaftPartitionServer;
import io.atomix.raft.zeebe.EntryValidator.NoopEntryValidator;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class ZeebeTestNode {

  public static final String CLUSTER_ID = "zeebe";
  private static final String HOST = "localhost";
  private static final int BASE_PORT = 10_000;
  private final Member member;
  private final Node node;
  private final File directory;
  private AtomixCluster cluster;
  private List<RaftPartition> partitions;
  private final MeterRegistry meterRegistry;

  public ZeebeTestNode(final int id, final File directory, final MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
    final String textualId = String.valueOf(id);

    this.directory = directory;
    node = Node.builder().withId(textualId).withHost(HOST).withPort(BASE_PORT + id).build();
    member = Member.member(MemberId.from(textualId), node.address());
  }

  public RaftPartitionServer getPartitionServer(final int id) {
    return getPartition(id).getServer();
  }

  RaftPartition getPartition(final int id) {
    return partitions.stream().filter(p -> p.id().id() == id).findFirst().orElse(null);
  }

  public CompletableFuture<Void> start(final Collection<ZeebeTestNode> nodes) {
    cluster = buildCluster(nodes);
    final Set<MemberId> members =
        nodes.stream().map(ZeebeTestNode::getMember).map(Member::id).collect(Collectors.toSet());
    members.add(member.id());

    final PartitionId partitionId = PartitionId.from("test", 1);
    final var priorityMap =
        members.stream()
            .collect(
                Collectors.toMap(memberId -> memberId, memberId -> Integer.valueOf(memberId.id())));
    final var primary = priorityMap.entrySet().stream().min(Entry.comparingByValue()).orElseThrow();
    final var partitionDistribution =
        Set.of(
            new PartitionMetadata(
                partitionId, members, priorityMap, primary.getValue(), primary.getKey()));

    partitions = buildPartitions(partitionDistribution);
    final var managementService =
        new DefaultPartitionManagementService(
            cluster.getMembershipService(), cluster.getCommunicationService());
    return cluster
        .start()
        .thenCompose(
            ignored ->
                CompletableFuture.allOf(
                    partitions.stream()
                        .map(
                            partition ->
                                partition.bootstrap(managementService, new NoopSnapshotStore()))
                        .toArray(CompletableFuture[]::new)));
  }

  private List<RaftPartition> buildPartitions(final Set<PartitionMetadata> partitionDistribution) {
    return partitionDistribution.stream()
        .map(
            partitionMetadata -> {
              final var raftStorageConfig = new RaftStorageConfig();
              raftStorageConfig.setSegmentSize(1024);
              final var raftPartitionConfig = new RaftPartitionConfig();
              raftPartitionConfig.setStorageConfig(raftStorageConfig);
              raftPartitionConfig.setPriorityElectionEnabled(false);
              raftPartitionConfig.setEntryValidator(new NoopEntryValidator());
              return new RaftPartition(
                  partitionMetadata,
                  raftPartitionConfig,
                  new File(new File(directory, "log"), "" + member.id()),
                  meterRegistry);
            })
        .toList();
  }

  private AtomixCluster buildCluster(final Collection<ZeebeTestNode> nodes) {
    return AtomixCluster.builder()
        .withAddress(node.address())
        .withClusterId(CLUSTER_ID)
        .withMembershipProvider(buildDiscoveryProvider(nodes))
        .withMemberId(getMemberId())
        .build();
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

  public Member getMember() {
    return member;
  }

  public CompletableFuture<Void> stop() {
    return CompletableFuture.allOf(
            partitions.stream().map(RaftPartition::close).toArray(CompletableFuture[]::new))
        .thenCompose(ignored -> cluster.stop());
  }

  public AtomixCluster getCluster() {
    return cluster;
  }

  @Override
  public String toString() {
    return "ZeebeTestNode{" + "member=" + member + '}';
  }
}
