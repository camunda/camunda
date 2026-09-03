/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.network;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.cluster.TestZeebePort;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.agrona.CloseHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Asserts that a cluster with {@code camunda.cluster.network.udp-enabled = false} uses no UDP,
 * proving it two independent ways at once.
 *
 * <p>The test holds a datagram socket on every broker's internal API port for the whole run. That
 * makes broker startup itself the first assertion: a bound datagram socket would fail to bind and
 * take the broker down with it, so a cluster that comes up cannot have bound one. The sniffers then
 * catch anything addressed to those ports, which is where cluster traffic would land, and the run
 * ends by asserting nothing arrived.
 *
 * <p>Both halves fail fast — at startup, or on the first datagram — rather than by waiting out a
 * timeout on an absence.
 *
 * <p>Scope of the guarantee: the flag governs Camunda's own cluster protocols. Infrastructure
 * protocols the JVM speaks underneath — DNS above all — still use UDP on ephemeral client ports.
 * The sniffers bind the brokers' internal API ports precisely so that they observe cluster traffic
 * and nothing else.
 */
@ZeebeIntegration
final class NoUdpSocketIT {

  @TestZeebe(autoStart = false)
  private final TestCluster cluster =
      TestCluster.builder()
          .withEmbeddedGateway(true)
          .withBrokersCount(3)
          .withPartitionsCount(1)
          .withReplicationFactor(3)
          .withBrokerConfig(
              broker ->
                  broker.withUnifiedConfig(
                      cfg -> cfg.getCluster().getNetwork().setUdpEnabled(false)))
          .build();

  private final Map<TestStandaloneBroker, DatagramSocket> sniffers = new LinkedHashMap<>();
  private final Queue<String> receivedDatagrams = new ConcurrentLinkedQueue<>();
  private final List<Thread> snifferThreads = new ArrayList<>();

  /**
   * Claims every broker's internal API port on UDP before the cluster starts, and drains anything
   * that arrives. {@code mappedPort} is resolvable before startup because ports are allocated
   * deterministically when the cluster is built.
   */
  @BeforeEach
  void beforeEach() throws IOException {
    for (final var broker : cluster.brokers().values()) {
      final var port = broker.mappedPort(TestZeebePort.CLUSTER);
      final var sniffer = new DatagramSocket(null);
      // the broker binds the wildcard address, so the sniffer must too; and a successful bind must
      // mean the port is genuinely free rather than shared, hence no address reuse
      sniffer.setReuseAddress(false);
      sniffer.bind(new InetSocketAddress(port));
      sniffers.put(broker, sniffer);

      final var thread = new Thread(() -> drain(sniffer), "udp-sniffer-" + port);
      thread.setDaemon(true);
      snifferThreads.add(thread);
      thread.start();
    }
  }

  @AfterEach
  void afterEach() {
    // leaked sockets keep the port claimed and flake the next run
    CloseHelper.quietCloseAll(sniffers.values());
    snifferThreads.forEach(Thread::interrupt);
  }

  @Test
  void shouldFormTheClusterWithoutAnyUdpTraffic() {
    // when - every broker starts even though the test holds its internal API port on UDP, which is
    // only possible because no datagram socket is bound
    cluster.start().awaitCompleteTopology();

    // then - not one datagram reached any broker's internal API port
    assertThat(receivedDatagrams).as("no cluster communication used UDP").isEmpty();
  }

  private void drain(final DatagramSocket sniffer) {
    final var buffer = new byte[65535];
    while (!Thread.currentThread().isInterrupted() && !sniffer.isClosed()) {
      try {
        final var packet = new DatagramPacket(buffer, buffer.length);
        sniffer.receive(packet);
        receivedDatagrams.add(packet.getSocketAddress() + " -> " + sniffer.getLocalPort());
      } catch (final IOException e) {
        return;
      }
    }
  }
}
