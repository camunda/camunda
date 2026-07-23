/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.AtomixCluster;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.Node;
import io.atomix.cluster.discovery.BootstrapDiscoveryProvider;
import io.atomix.cluster.impl.DiscoveryMembershipProtocol;
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.dynamic.config.changes.ClusterChangeExecutor.NoopClusterChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeCoordinator;
import io.camunda.zeebe.dynamic.config.changes.ModeChangeExecutor.NoopModeChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.NoopPartitionChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.PartitionScalingChangeExecutor.NoopPartitionScalingChangeExecutor;
import io.camunda.zeebe.dynamic.config.gossip.ClusterConfigurationGossiperConfig;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import io.camunda.zeebe.util.Either;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies {@link ClusterConfigurationManagerService} wired for the new multi-partition-group model
 * (the {@code useNewConfig} test seam, since {@link
 * ClusterConfigurationManagerService#USE_NEW_CONFIG} is a compile-time constant in production): the
 * multi-group configuration is generated from static configuration via {@link
 * CurrentClusterConfigurationInitializer.StaticInitializer}, converges across nodes, and a
 * per-tenant applier registered through {@link
 * ClusterConfigurationManagerService#registerPartitionGroupChangeExecutors} actually applies a
 * change end to end.
 */
final class ClusterConfigurationManagerServiceNewModelTest {

  private static final Duration INITIALIZATION_TIMEOUT = Duration.ofSeconds(30);

  @TempDir Path rootDir;
  private final ActorScheduler actorScheduler = ActorScheduler.newActorScheduler().build();
  private final List<Node> clusterNodes = List.of(createNode("0"), createNode("1"));
  private final Map<Integer, TestNode> nodes = new HashMap<>();
  @AutoClose private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

  @BeforeEach
  void setup() {
    actorScheduler.start();
    clusterNodes.forEach(
        node ->
            nodes.put(
                Integer.parseInt(node.id().id()),
                createService(rootDir, createClusterNode(node, clusterNodes))));
  }

  @AfterEach
  void tearDown() {
    nodes.values().forEach(TestNode::close);
    actorScheduler.stop();
  }

  @Test
  void shouldGenerateMultiGroupConfigurationFromStaticConfigOnAllNodes() {
    // when
    nodes.values().forEach(this::startNode);

    // then — every node independently generated the same multi-group configuration
    nodes
        .values()
        .forEach(
            node ->
                Awaitility.await("node initializes the multi-group configuration")
                    .timeout(INITIALIZATION_TIMEOUT)
                    .untilAsserted(
                        () -> {
                          final var config = node.service().getMultiConfiguration().join();
                          assertThat(config.globalConfiguration().members().keySet())
                              .containsExactlyInAnyOrder(MemberId.from("0"), MemberId.from("1"));
                          assertThat(config.partitionGroups())
                              .containsOnlyKeys(CurrentClusterConfiguration.DEFAULT_GROUP);
                        }));

    // and — the legacy-shaped read compat also reflects the same members
    nodes
        .values()
        .forEach(
            node ->
                assertThat(node.service().getClusterTopology().join().members().keySet())
                    .containsExactlyInAnyOrder(MemberId.from("0"), MemberId.from("1")));
  }

  @Test
  void shouldApplyChangeThroughPerTenantAppliers() {
    // given
    nodes.values().forEach(this::startNode);
    Awaitility.await("nodes initialize")
        .timeout(INITIALIZATION_TIMEOUT)
        .untilAsserted(
            () ->
                nodes
                    .values()
                    .forEach(
                        node ->
                            assertThat(
                                    node.service()
                                        .getMultiConfiguration()
                                        .join()
                                        .partitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP)
                                        .members())
                                .containsKeys(MemberId.from("0"), MemberId.from("1"))));

    final ClusterConfigurationManagerService service = nodes.get(0).service();
    final ConfigurationChangeCoordinator coordinator =
        service.getTopologyChangeCoordinator().orElseThrow();

    // when — member 1 leaves partition 1, which has 2 replicas
    coordinator
        .applyOperations(
            ignore -> Either.right(List.of(new PartitionLeaveOperation(MemberId.from("1"), 1, 1))))
        .join();

    // then — the change is applied via the registered per-tenant appliers
    Awaitility.await("the partition-group change completes")
        .timeout(INITIALIZATION_TIMEOUT)
        .untilAsserted(
            () -> {
              final var group =
                  service
                      .getMultiConfiguration()
                      .join()
                      .partitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP);
              assertThat(group.hasPendingChanges()).isFalse();
              assertThat(group.hasMember(MemberId.from("1"))).isFalse();
            });
  }

  private Node createNode(final String id) {
    return Node.builder().withId(id).withPort(SocketUtil.getNextAddress().getPort()).build();
  }

  private AtomixCluster createClusterNode(final Node localNode, final Collection<Node> nodes) {
    return AtomixCluster.builder(meterRegistry)
        .withAddress(localNode.address())
        .withMemberId(localNode.id().id())
        .withMembershipProvider(new BootstrapDiscoveryProvider(nodes))
        .withMembershipProtocol(new DiscoveryMembershipProtocol())
        .build();
  }

  private TestNode createService(final Path tempDir, final AtomixCluster cluster) {
    final var service =
        new ClusterConfigurationManagerService(
            tempDir.resolve(cluster.getMembershipService().getLocalMember().id().id()),
            cluster.getCommunicationService(),
            cluster.getMembershipService(),
            new ClusterConfigurationGossiperConfig(
                Duration.ofSeconds(1), Duration.ofMillis(100), 2, Duration.ofSeconds(1)),
            new NoopClusterChangeExecutor(),
            meterRegistry,
            true);
    return new TestNode(cluster, service);
  }

  private void startNode(final TestNode node) {
    node.start(actorScheduler, getPartitionDistribution());
  }

  private Set<PartitionMetadata> getPartitionDistribution() {
    return Set.of(
        new PartitionMetadata(
            new PartitionId(CurrentClusterConfiguration.DEFAULT_GROUP, 1),
            Set.of(MemberId.from("0"), MemberId.from("1")),
            Map.of(MemberId.from("0"), 2, MemberId.from("1"), 1),
            2,
            MemberId.from("0")));
  }

  private record TestNode(AtomixCluster cluster, ClusterConfigurationManagerService service) {

    void start(final ActorScheduler actorScheduler, final Set<PartitionMetadata> partitions) {
      cluster.start().join();
      final var clusterMembers =
          Set.of(MemberId.from("0"), MemberId.from("1")).stream()
              .collect(Collectors.toUnmodifiableSet());
      final var startFuture =
          service.start(
              actorScheduler,
              new StaticConfiguration(
                  new ControllablePartitionDistributor().withPartitions(partitions),
                  clusterMembers,
                  cluster.getMembershipService().getLocalMember().id(),
                  List.of(new PartitionId(CurrentClusterConfiguration.DEFAULT_GROUP, 1)),
                  2,
                  DynamicPartitionConfig.init(),
                  "clusterId"));
      startFuture.onComplete(
          (ignore, error) -> {
            if (error == null) {
              service.registerPartitionGroupChangeExecutors(
                  CurrentClusterConfiguration.DEFAULT_GROUP,
                  new NoopPartitionChangeExecutor(),
                  new NoopPartitionScalingChangeExecutor(),
                  new NoopModeChangeExecutor());
            }
          },
          Runnable::run);
      startFuture.join();
    }

    void close() {
      service.closeAsync().join();
      cluster.stop().join();
    }
  }
}
