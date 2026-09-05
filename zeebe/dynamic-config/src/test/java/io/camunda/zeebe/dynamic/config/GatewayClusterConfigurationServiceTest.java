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
import io.camunda.zeebe.dynamic.config.ClusterConfigurationUpdateNotifier.ClusterConfigurationUpdateListener;
import io.camunda.zeebe.dynamic.config.gossip.ClusterConfigurationGossiper;
import io.camunda.zeebe.dynamic.config.gossip.ClusterConfigurationGossiperConfig;
import io.camunda.zeebe.dynamic.config.metrics.TopologyMetrics;
import io.camunda.zeebe.dynamic.config.serializer.ProtoBufSerializer;
import io.camunda.zeebe.dynamic.config.state.BrokerState;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PhasedChangeState;
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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.agrona.CloseHelper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class GatewayClusterConfigurationServiceTest {

  private final ActorScheduler actorScheduler = ActorScheduler.newActorScheduler().build();
  private final List<Node> clusterNodes =
      List.of(createNode("1"), createNode("2"), createNode("3"));
  private final ClusterConfigurationGossiperConfig config =
      new ClusterConfigurationGossiperConfig(
          Duration.ofMillis(100),
          Duration.ofSeconds(1),
          0,
          Duration.ofSeconds(1),
          Duration.ofSeconds(1));
  @AutoClose private MeterRegistry meterRegistry = new SimpleMeterRegistry();
  private TestBroker broker1;
  private TestBroker broker3;
  private AtomixCluster gatewayCluster;
  private GatewayClusterConfigurationService gateway;

  @BeforeEach
  void setup() {
    actorScheduler.start();
  }

  @AfterEach
  void afterEach() {
    if (gateway != null) {
      gateway.close();
    }
    if (gatewayCluster != null) {
      gatewayCluster.stop().join();
    }
    CloseHelper.quietCloseAll(broker1, broker3, actorScheduler);
  }

  @Test
  void shouldReceiveNewModelConfiguration() {
    // given — a broker gossiping the new-model configuration, and a gateway wired for the new
    // model
    broker1 = new TestBroker(createClusterNode(clusterNodes.get(0), clusterNodes), true);
    broker1.start();
    startGateway(clusterNodes.get(1));

    final var brokerConfiguration =
        CurrentClusterConfiguration.fromLegacy(
            ClusterConfiguration.init()
                .addMember(broker1.id(), MemberState.initializeAsActive(Map.of())));

    final var received = new AtomicReference<CurrentClusterConfiguration>();
    gateway.addUpdateListener(
        new ClusterConfigurationUpdateListener() {
          @Override
          public void onClusterConfigurationUpdated(
              final ClusterConfiguration clusterConfiguration) {
            // should not be reached: the new-model handler is wired, so field 2 is always
            // preferred
            throw new IllegalStateException(
                "Legacy listener should not be called when new-model field is populated");
          }

          @Override
          public void onClusterConfigurationUpdated(
              final CurrentClusterConfiguration clusterConfiguration) {
            received.set(clusterConfiguration);
          }
        });

    // when
    broker1.setCurrentClusterConfiguration(brokerConfiguration);

    // then — the gateway receives and merges the full new-model configuration, not a legacy
    // single-group projection
    Awaitility.await("The gateway has received the new-model configuration via gossip")
        .untilAsserted(() -> assertThat(received.get()).isEqualTo(brokerConfiguration));
  }

  @Test
  void shouldRelayNewModelConfigurationToOtherBrokers() {
    // given — a broker gossips a new-model configuration with a non-default physical tenant
    // group; the gateway is wired for the new model and sits between the two brokers
    broker1 = new TestBroker(createClusterNode(clusterNodes.get(0), clusterNodes), true);
    broker3 = new TestBroker(createClusterNode(clusterNodes.get(2), clusterNodes), true);
    broker1.start();
    broker3.start();
    startGateway(clusterNodes.get(1));

    final var tenantGroup =
        new PartitionGroupConfiguration(
            PartitionGroupConfiguration.INITIAL_VERSION,
            PartitionGroupConfiguration.INITIAL_INCARNATION_NUMBER,
            Map.of(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());
    final var brokerConfiguration =
        new CurrentClusterConfiguration(
            CurrentClusterConfiguration.INITIAL_VERSION,
            GlobalConfiguration.init().addMember(broker1.id(), BrokerState.initializeAsActive()),
            Map.of("tenanta", tenantGroup),
            PhasedChangeState.empty());

    // when — broker1 gossips the new-model configuration; it must reach broker3 relayed through
    // the gateway (before this fix, the gateway never populated field 2 at all, so it could only
    // ever relay a lossy single-group legacy projection)
    broker1.setCurrentClusterConfiguration(brokerConfiguration);

    // then
    Awaitility.await("Broker 3 has received the full new-model configuration via the gateway")
        .untilAsserted(
            () -> assertThat(broker3.currentClusterConfiguration).isEqualTo(brokerConfiguration));
    assertThat(broker3.currentClusterConfiguration.hasPartitionGroup("tenanta")).isTrue();
  }

  @Test
  void shouldRelayLegacyOnlyBrokerConfigurationWithoutCorruptingConvergence() {
    // given — broker1 is not yet upgraded (legacy handler only, e.g. mid rolling-upgrade); the
    // gateway and broker3 are both wired for the new model
    broker1 = new TestBroker(createClusterNode(clusterNodes.get(0), clusterNodes), false);
    broker3 = new TestBroker(createClusterNode(clusterNodes.get(2), clusterNodes), true);
    broker1.start();
    broker3.start();
    startGateway(clusterNodes.get(1));

    final var brokerTopology =
        ClusterConfiguration.init()
            .addMember(broker1.id(), MemberState.initializeAsActive(Map.of()));
    final var reconstructed = CurrentClusterConfiguration.fromLegacy(brokerTopology);

    // when — the not-yet-upgraded broker gossips only the legacy field; the gateway falls back to
    // fromLegacy (same as any new-model peer would) and relays the result onward
    broker1.setTopology(brokerTopology);

    // then — broker3 converges on the reconstructed configuration via the gateway, with no merge
    // exception and no runaway version growth from repeated re-derivation (the gateway now
    // dual-writes and only re-gossips when the merged value actually changes, same as a real
    // broker; see ClusterConfigurationGossiper#updateCurrentClusterConfiguration)
    Awaitility.await("Broker 3 has received the reconstructed configuration via the gateway")
        .untilAsserted(
            () -> assertThat(broker3.currentClusterConfiguration).isEqualTo(reconstructed));

    // and — the reconstruction is stable: waiting longer doesn't change the converged value (no
    // endless re-derivation loop through the gateway)
    Awaitility.await()
        .during(Duration.ofSeconds(1))
        .untilAsserted(
            () -> assertThat(broker3.currentClusterConfiguration).isEqualTo(reconstructed));
  }

  private void startGateway(final Node node) {
    gatewayCluster = createClusterNode(node, clusterNodes);
    gatewayCluster.start().join();
    gateway =
        new GatewayClusterConfigurationService(
            gatewayCluster.getCommunicationService(),
            gatewayCluster.getMembershipService(),
            config,
            meterRegistry);
    gateway.start(actorScheduler).join();
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

  /** Minimal stand-in for a broker's {@code ClusterConfigurationManagerImpl}-driven gossiping. */
  private final class TestBroker extends Actor {
    private final ClusterConfigurationGossiper gossiper;
    private final AtomixCluster atomixCluster;
    private ClusterConfiguration clusterConfiguration;
    private CurrentClusterConfiguration currentClusterConfiguration;

    private TestBroker(final AtomixCluster atomixCluster, final boolean useNewModelHandler) {
      super("Node-" + atomixCluster.getMembershipService().getLocalMember().id());
      gossiper =
          new ClusterConfigurationGossiper(
              this,
              atomixCluster.getCommunicationService(),
              atomixCluster.getMembershipService(),
              new ProtoBufSerializer(),
              config,
              useNewModelHandler ? null : this::mergeTopology,
              useNewModelHandler ? this::mergeCurrentClusterConfiguration : null,
              new TopologyMetrics(meterRegistry));
      this.atomixCluster = atomixCluster;
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

    void setCurrentClusterConfiguration(
        final CurrentClusterConfiguration currentClusterConfiguration) {
      this.currentClusterConfiguration = currentClusterConfiguration;
      gossiper.updateCurrentClusterConfiguration(currentClusterConfiguration);
    }

    private void mergeCurrentClusterConfiguration(final CurrentClusterConfiguration received) {
      currentClusterConfiguration =
          currentClusterConfiguration == null
              ? received
              : currentClusterConfiguration.merge(received);
      gossiper.updateCurrentClusterConfiguration(currentClusterConfiguration);
    }

    public MemberId id() {
      return atomixCluster.getMembershipService().getLocalMember().id();
    }
  }
}
