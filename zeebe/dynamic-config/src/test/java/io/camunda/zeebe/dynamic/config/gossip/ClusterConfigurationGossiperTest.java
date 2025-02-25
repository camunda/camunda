/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.gossip;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.AtomixCluster;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.Node;
import io.atomix.cluster.discovery.BootstrapDiscoveryProvider;
import io.atomix.cluster.impl.DiscoveryMembershipProtocol;
import io.camunda.zeebe.dynamic.config.metrics.TopologyMetrics;
import io.camunda.zeebe.dynamic.config.serializer.ProtoBufSerializer;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.testing.TestActorFuture;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.agrona.CloseHelper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class ClusterConfigurationGossiperTest {

  private final ActorScheduler actorScheduler = ActorScheduler.newActorScheduler().build();
  private final List<Node> clusterNodes =
      List.of(createNode("1"), createNode("2"), createNode("3"));
  private TestGossiper node1;
  private TestGossiper node2;
  private TestGossiper node3;
  @AutoClose private MeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final TopologyMetrics topologyMetrics = new TopologyMetrics(meterRegistry);

  @BeforeEach
  void setup() {
    actorScheduler.start();
  }

  @AfterEach
  void afterEach() {
    CloseHelper.quietCloseAll(node1, node2, node3, actorScheduler);
  }

  @Test
  void shouldPropagateTopologyUpdate() {
    // given
    final var config =
        new ClusterConfigurationGossiperConfig(Duration.ofMillis(100), Duration.ofSeconds(1), 0);
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
        ClusterConfiguration.init().addMember(node1.id(), MemberState.initializeAsActive(Map.of()));

    // when
    node1.setTopology(node1Topology);

    // then
    Awaitility.await("Node 2 has received topology via gossip")
        .untilAsserted(() -> assertThat(node2.clusterConfiguration).isEqualTo(node1Topology));
    Awaitility.await("Node 3 has received topology via gossip")
        .untilAsserted(() -> assertThat(node3.clusterConfiguration).isEqualTo(node1Topology));
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
    private final ClusterConfigurationGossiper gossiper;
    private final AtomixCluster atomixCluster;
    private ClusterConfiguration clusterConfiguration;

    private TestGossiper(
        final AtomixCluster atomixCluster,
        final ClusterConfigurationGossiperConfig config,
        final TopologyMetrics topologyMetrics) {

      gossiper =
          new ClusterConfigurationGossiper(
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

    void setTopology(final ClusterConfiguration clusterConfiguration) {
      this.clusterConfiguration = clusterConfiguration;
      gossiper.updateClusterConfiguration(clusterConfiguration);
    }

    private ActorFuture<ClusterConfiguration> mergeTopology(final ClusterConfiguration t) {
      clusterConfiguration = clusterConfiguration == null ? t : t.merge(clusterConfiguration);
      return TestActorFuture.completedFuture(clusterConfiguration);
    }

    public MemberId id() {
      return atomixCluster.getMembershipService().getLocalMember().id();
    }
  }
}
