/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.clustering.dynamic;

import static io.camunda.zeebe.it.cluster.clustering.dynamic.Utils.assertChangeIsPlanned;
import static org.assertj.core.api.Assertions.assertThat;

import eu.rekawek.toxiproxy.model.ToxicDirection;
import io.atomix.cluster.MemberId;
import io.camunda.zeebe.management.cluster.GetTopologyResponse;
import io.camunda.zeebe.management.cluster.PartitionStateCode;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestGateway;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.cluster.TestZeebePort;
import io.camunda.zeebe.qa.util.testcontainers.ProxyRegistry;
import io.camunda.zeebe.qa.util.topology.ClusterActuatorAssert;
import java.io.IOException;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Covers the transport-level shape of the partition join deadlock (<a
 * href="https://github.com/camunda/camunda/issues/60090">#60090</a>): a joining member whose
 * messaging is slow rather than broken, for longer than a join attempt is allowed to take, across
 * several attempts. Each failed attempt tears the joiner's partition down and unregisters its
 * messaging handlers before the next attempt re-creates them; the deterministic Raft tests cannot
 * exercise that, since they run on an in-memory protocol.
 *
 * <p>Since joins are two-phase, the leader admits the joiner as a non-voting learner and answers at
 * once; the part of a join that depends on the joiner's own connectivity is catching up to the
 * admitting entry, bounded by the join catch-up timeout. Every attempt starts with the leader
 * configuring the re-created member and then replicating to it, so delaying each message towards
 * the joiner by more than half the catch-up timeout makes every attempt time out. The change stays
 * pending until the messages are fast again.
 *
 * <p>Toxiproxy proxies TCP only; the members' UDP gossip does not pass it, so a short sync interval
 * propagates membership over TCP instead, as in {@code AdvertisedAddressTest}. The container is
 * started per test rather than per class, so that a rerun of the test gets working proxies.
 */
final class SlowJoinerPartitionJoinTest {
  private static final String TOXIPROXY_IMAGE = "shopify/toxiproxy:2.1.0";
  private static final Duration JOIN_CATCH_UP_TIMEOUT = Duration.ofSeconds(2);
  private static final Duration MESSAGE_DELAY = Duration.ofSeconds(3);
  // The reconciler retries a failed operation with a backoff that starts at ten seconds, so this is
  // room for three failed attempts.
  private static final Duration SLOW_PERIOD = Duration.ofSeconds(40);
  private static final Duration JOIN_TIMEOUT = Duration.ofMinutes(3);

  private static final MemberId JOINER = MemberId.from("1");
  private static final int PARTITION = 1;

  @Test
  void shouldCompleteJoinAfterJoinerWasTooSlowToCatchUpForSeveralAttempts() throws IOException {
    try (final var toxiproxy =
        ProxyRegistry.addExposedPorts(
                new ToxiproxyContainer(DockerImageName.parse(TOXIPROXY_IMAGE)))
            .withAccessToHost(true)) {
      toxiproxy.start();
      final var proxies = new ProxyRegistry(toxiproxy);
      try (final var cluster = buildCluster(toxiproxy, proxies)) {
        // given - a cluster in which every message towards the joiner is delayed for longer than
        // a join attempt may take to catch up
        cluster.start().awaitCompleteTopology();
        final var joinerProxy =
            proxies.getOrCreateHostProxy(
                cluster.brokers().get(JOINER).mappedPort(TestZeebePort.CLUSTER));
        final var delay =
            joinerProxy
                .proxy()
                .toxics()
                .latency("slow-joiner", ToxicDirection.UPSTREAM, MESSAGE_DELAY.toMillis());

        // when - the joiner is asked to join a partition it does not have
        final var actuator = ClusterActuator.of(cluster.availableGateway());
        final var response = actuator.joinPartition(Integer.parseInt(JOINER.id()), PARTITION, 1);
        assertChangeIsPlanned(response);

        // then - no attempt completes while the joiner is slow: the change stays pending and the
        // partition stays in the joining state across several attempts ...
        Awaitility.await("join does not complete while the joiner is slow")
            .during(SLOW_PERIOD)
            .atMost(SLOW_PERIOD.plusSeconds(10))
            .untilAsserted(
                () -> {
                  final var topology = actuator.getTopology();
                  assertThat(topology.getPendingChange()).isNotNull();
                  assertThat(isCompleted(topology, response.getChangeId())).isFalse();
                  ClusterActuatorAssert.assertThat(cluster)
                      .brokerHasPartitionAtState(
                          Integer.parseInt(JOINER.id()), PARTITION, PartitionStateCode.JOINING);
                });

        // ... and the join completes once messages reach the joiner in time again
        delay.remove();
        Awaitility.await("join completes once the joiner is fast again")
            .timeout(JOIN_TIMEOUT)
            .untilAsserted(
                () -> ClusterActuatorAssert.assertThat(cluster).hasCompletedChanges(response));
        ClusterActuatorAssert.assertThat(cluster)
            .hasAppliedChanges(response)
            .brokerHasPartition(Integer.parseInt(JOINER.id()), PARTITION);
        cluster.awaitHealthyTopology();
      }
    }
  }

  private static boolean isCompleted(final GetTopologyResponse topology, final long changeId) {
    final var lastChange = topology.getLastChange();
    return lastChange != null && lastChange.getId() == changeId;
  }

  private static TestCluster buildCluster(
      final ToxiproxyContainer toxiproxy, final ProxyRegistry proxies) {
    final var cluster =
        TestCluster.builder()
            .withEmbeddedGateway(false)
            .withGatewaysCount(1)
            .withBrokersCount(2)
            .withPartitionsCount(2)
            .withReplicationFactor(1)
            .withBrokerConfig(broker -> configureBroker(broker, toxiproxy, proxies))
            .withGatewayConfig(gateway -> configureGateway(gateway, toxiproxy, proxies))
            .build();
    // The cluster is not aware of the proxies, so the initial contact points have to be rebuilt.
    final var contactPoints =
        cluster.brokers().values().stream()
            .map(
                broker ->
                    proxiedAddress(broker.mappedPort(TestZeebePort.CLUSTER), toxiproxy, proxies))
            .toList();
    cluster
        .brokers()
        .values()
        .forEach(
            b ->
                b.withUnifiedConfig(
                    cfg -> cfg.getCluster().setInitialContactPoints(contactPoints)));
    cluster
        .gateways()
        .values()
        .forEach(
            g ->
                g.withUnifiedConfig(
                    cfg -> cfg.getCluster().setInitialContactPoints(contactPoints)));
    return cluster;
  }

  private static void configureBroker(
      final TestStandaloneBroker broker,
      final ToxiproxyContainer toxiproxy,
      final ProxyRegistry proxies) {
    final var internalApiProxy =
        proxies.getOrCreateHostProxy(broker.mappedPort(TestZeebePort.CLUSTER));
    broker
        .withCreateSchema(false)
        .withUnifiedConfig(
            cfg -> {
              cfg.getCluster().getRaft().setJoinCatchUpTimeout(JOIN_CATCH_UP_TIMEOUT);
              final var internalApi = cfg.getCluster().getNetwork().getInternalApi();
              internalApi.setAdvertisedHost(toxiproxy.getHost());
              internalApi.setAdvertisedPort(
                  toxiproxy.getMappedPort(internalApiProxy.internalPort()));
              final var membership = cfg.getCluster().getMembership();
              membership.setSyncInterval(Duration.ofMillis(100));
              // Membership probes must outlive the injected delay, or the slow joiner is declared
              // dead and messaging stops addressing it altogether. The test is about a member
              // that is slow, not one that is gone.
              membership.setProbeTimeout(MESSAGE_DELAY.multipliedBy(2));
              membership.setFailureTimeout(Duration.ofMinutes(1));
            });
  }

  private static void configureGateway(
      final TestGateway<?> gateway,
      final ToxiproxyContainer toxiproxy,
      final ProxyRegistry proxies) {
    final var internalApiProxy =
        proxies.getOrCreateHostProxy(gateway.mappedPort(TestZeebePort.CLUSTER));
    gateway.withUnifiedConfig(
        cfg -> {
          final var internalApi = cfg.getCluster().getNetwork().getInternalApi();
          internalApi.setAdvertisedHost(toxiproxy.getHost());
          internalApi.setAdvertisedPort(toxiproxy.getMappedPort(internalApiProxy.internalPort()));
        });
  }

  private static String proxiedAddress(
      final int hostPort, final ToxiproxyContainer toxiproxy, final ProxyRegistry proxies) {
    final var proxy = proxies.getOrCreateHostProxy(hostPort);
    return toxiproxy.getHost() + ":" + toxiproxy.getMappedPort(proxy.internalPort());
  }
}
