/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology.gossip;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.AtomixCluster;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.Node;
import io.atomix.cluster.discovery.BootstrapDiscoveryProvider;
import io.atomix.cluster.impl.DiscoveryMembershipProtocol;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.testing.TestActorFuture;
import io.camunda.zeebe.test.util.junit.AutoCloseResources.AutoCloseResource;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import io.camunda.zeebe.topology.metrics.TopologyMetrics;
import io.camunda.zeebe.topology.serializer.ProtoBufSerializer;
import io.camunda.zeebe.topology.state.ClusterTopology;
import io.camunda.zeebe.topology.state.MemberState;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.agrona.CloseHelper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

final class ClusterTopologyGossiperTest {

  private final ActorScheduler actorScheduler = ActorScheduler.newActorScheduler().build();
  private final List<Node> clusterNodes =
      List.of(createNode("1"), createNode("2"), createNode("3"));
  private TestGossiper node1;
  private TestGossiper node2;
  private TestGossiper node3;
  private MeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final TopologyMetrics topologyMetrics = new TopologyMetrics(meterRegistry);

  @BeforeEach
  void setup() {
    actorScheduler.start();
  }

  @AfterEach
  void afterEach() {
    CloseHelper.quietCloseAll(node1, node2, node3, actorScheduler);
  }

  @ParameterizedTest
  @MethodSource("provideConfig")
  void shouldPropagateTopologyUpdate(final ClusterTopologyGossiperConfig config) {
    // given
    node1 =
        new TestGossiper(
            createClusterNode(clusterNodes.get(0), clusterNodes), config, topologyMetrics);
    node2 =
        new TestGossiper(
            createClusterNode(clusterNodes.get(1), clusterNodes), config, topologyMetrics);
    node3 =
        new TestGossiper(
            createClusterNode(clusterNodes.get(2), clusterNodes), config, topologyMetrics);

    node1.start();
    node2.start();
    node3.start();

    final var node1Topology =
        ClusterTopology.init().addMember(node1.id(), MemberState.initializeAsActive(Map.of()));

    // when
    node1.setTopology(node1Topology);

    // then
    Awaitility.await("Node 2 has received topology via gossip")
        .untilAsserted(() -> assertThat(node2.clusterTopology).isEqualTo(node1Topology));
    Awaitility.await("Node 3 has received topology via gossip")
        .untilAsserted(() -> assertThat(node3.clusterTopology).isEqualTo(node1Topology));
  }

  private static Stream<Arguments> provideConfig() {
    return Stream.of(
        Arguments.of(
            Named.of(
                "by gossip", // Disable sync
                new ClusterTopologyGossiperConfig(
                    false, Duration.ofMinutes(10), Duration.ofSeconds(1), 2))),
        Arguments.of(
            Named.of(
                "by sync", // Set gossipFanout to 0
                new ClusterTopologyGossiperConfig(
                    true, Duration.ofMillis(100), Duration.ofSeconds(1), 0))));
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

  private final class TestGossiper extends Actor {
    private final ClusterTopologyGossiper gossiper;
    private final AtomixCluster atomixCluster;
    private ClusterTopology clusterTopology;

    private TestGossiper(
        final AtomixCluster atomixCluster,
        final ClusterTopologyGossiperConfig config,
        final TopologyMetrics topologyMetrics) {

      gossiper =
          new ClusterTopologyGossiper(
              this,
              atomixCluster.getCommunicationService(),
              atomixCluster.getMembershipService(),
              new ProtoBufSerializer(),
              config,
              this::mergeTopology,
              topologyMetrics);
      this.atomixCluster = atomixCluster;
    }

    @Override
    public String getName() {
      return "Node-" + id();
    }

    @Override
    public void close() {
      atomixCluster.stop().join();
    }

    private void start() {
      atomixCluster.start().join();
      actorScheduler.submitActor(this).join();
      gossiper.start();
    }

    void setTopology(final ClusterTopology clusterTopology) {
      this.clusterTopology = clusterTopology;
      gossiper.updateClusterTopology(clusterTopology);
    }

    private ActorFuture<ClusterTopology> mergeTopology(final ClusterTopology t) {
      clusterTopology = clusterTopology == null ? t : t.merge(clusterTopology);
      return TestActorFuture.completedFuture(clusterTopology);
    }

    public MemberId id() {
      return atomixCluster.getMembershipService().getLocalMember().id();
    }
  }
}
