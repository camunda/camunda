/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.network;

import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.cluster.TestZeebePort;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.SocatContainer;

/**
 * Forms a cluster whose only routable path between brokers is TCP, and asserts it still reaches a
 * complete topology with {@code camunda.cluster.network.udp-enabled = false}.
 *
 * <p>This is the deployment the flag exists for: an encryption mandate or a service mesh where UDP
 * was never routed. Socat is used rather than Toxiproxy because it forwards TCP and nothing else,
 * which states the absence of a UDP path rather than relying on a proxy that merely happens not to
 * forward datagrams. Testcontainers' host port forwarding is TCP-only as well, so there are two
 * independent reasons no datagram can cross.
 *
 * <p>That no UDP socket is bound at all is asserted by {@link NoUdpSocketIT} and by {@code
 * NettyNetworkServiceTest.TransportSelectionTest}; this test does not repeat it.
 */
@ZeebeIntegration
final class NoUdpMembershipGossipIT {

  private final SocatContainer socat = new SocatContainer();

  @TestZeebe(autoStart = false)
  private final TestCluster cluster =
      TestCluster.builder()
          .withEmbeddedGateway(true)
          .withBrokersCount(3)
          .withPartitionsCount(1)
          .withReplicationFactor(3)
          .build();

  private final Map<TestStandaloneBroker, Integer> socatPorts = new LinkedHashMap<>();

  /**
   * Socat's targets are fixed when the container starts, so the proxies cannot be created lazily
   * from inside a broker config callback the way the Toxiproxy-based tests do. Ports are exposed
   * and targets registered first, then the brokers are pointed at the proxy.
   */
  @BeforeEach
  void beforeEach() {
    final var brokers = List.copyOf(cluster.brokers().values());

    final var hostPorts = new ArrayList<Integer>();
    for (final var broker : brokers) {
      hostPorts.add(broker.mappedPort(TestZeebePort.CLUSTER));
    }
    Testcontainers.exposeHostPorts(hostPorts.stream().mapToInt(Integer::intValue).toArray());

    var listenPort = 30000;
    for (var index = 0; index < brokers.size(); index++) {
      socat.withTarget(listenPort, "host.testcontainers.internal", hostPorts.get(index));
      socatPorts.put(brokers.get(index), listenPort);
      listenPort++;
    }
    socat.start();

    final var proxiedContactPoints = brokers.stream().map(this::proxiedAddress).toList();

    brokers.forEach(
        broker ->
            broker.withUnifiedConfig(
                cfg -> {
                  // advertise the proxy, so peers reach this broker only over the TCP-only path
                  final var internalApi = cfg.getCluster().getNetwork().getInternalApi();
                  final var proxied = proxiedAddress(broker).split(":");
                  internalApi.setAdvertisedHost(proxied[0]);
                  internalApi.setAdvertisedPort(Integer.parseInt(proxied[1]));

                  cfg.getCluster().getNetwork().setUdpEnabled(false);
                  // the cluster is unaware of the proxy, so its default contact points would
                  // bypass it
                  cfg.getCluster().setInitialContactPoints(proxiedContactPoints);
                }));
  }

  @AfterEach
  void afterEach() {
    socat.stop();
  }

  @Test
  void shouldFormACompleteTopologyOverTcpOnly() {
    // when
    cluster.start();

    // then
    cluster.awaitCompleteTopology();
  }

  private String proxiedAddress(final TestStandaloneBroker broker) {
    return socat.getHost() + ":" + socat.getMappedPort(socatPorts.get(broker));
  }
}
