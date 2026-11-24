/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.network;

import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestZeebePort;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Forms a cluster where some brokers have UDP disabled and others do not, as happens halfway
 * through a rolling change of {@code camunda.cluster.network.udp-enabled}, and asserts it still
 * reaches a complete topology.
 *
 * <p>Gossip from a broker with UDP disabled is sent over TCP. A broker with UDP enabled only hears
 * it because it listens on TCP as well as UDP, which is what this test guards.
 *
 * <p>For that to be the only way to converge, membership has to depend on that gossip. Broker 0 has
 * UDP disabled and is the only contact point; brokers 1 and 2 have UDP enabled and can learn of
 * each other only through broker 0's gossip. The periodic TCP sync is pushed out well past the
 * deadline, and the probe timeout is raised so that a slow probe does not trigger an indirect
 * probe, which would teach the probed broker about the prober over TCP.
 */
@ZeebeIntegration
final class MixedUdpModeIT {

  // far beyond the topology deadline, so that sync cannot stand in for gossip
  private static final Duration SYNC_INTERVAL = Duration.ofMinutes(5);
  // the test default of 100ms can be exceeded on a loaded machine, which triggers indirect probes
  private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(5);
  private static final Duration TOPOLOGY_DEADLINE = Duration.ofSeconds(30);

  @TestZeebe(autoStart = false)
  private final TestCluster cluster =
      TestCluster.builder()
          .withEmbeddedGateway(true)
          .withBrokersCount(3)
          .withPartitionsCount(1)
          .withReplicationFactor(3)
          .build();

  @BeforeEach
  void beforeEach() {
    final var brokers = List.copyOf(cluster.brokers().values());
    final var seed = brokers.getFirst();
    final var seedContactPoints = List.of(seed.address(TestZeebePort.CLUSTER));

    brokers.forEach(
        broker ->
            broker.withUnifiedConfig(
                cfg -> {
                  cfg.getCluster().getNetwork().setUdpEnabled(broker != seed);
                  // listing every broker would let TCP probes alone converge the cluster; the
                  // seed is listed for itself too, as a multi-broker cluster requires at least one
                  // contact point
                  cfg.getCluster().setInitialContactPoints(seedContactPoints);
                  cfg.getCluster().getMembership().setSyncInterval(SYNC_INTERVAL);
                  cfg.getCluster().getMembership().setProbeTimeout(PROBE_TIMEOUT);
                }));
  }

  @Test
  void shouldFormACompleteTopologyWithMixedUdpModes() {
    // when
    cluster.start();

    // then
    cluster.awaitCompleteTopology(TOPOLOGY_DEADLINE);
  }
}
