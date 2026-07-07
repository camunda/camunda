/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.cluster;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.discovery.BootstrapDiscoveryProvider;
import io.atomix.cluster.messaging.impl.NettyUnicastService;
import io.atomix.utils.net.Address;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.agrona.CloseHelper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;

/**
 * Tests forming a cluster where all members bind to ephemeral ports (0) assigned by the OS. Since
 * no port is known before a member is started, members are started in sequence: a seed member
 * starts first, and the remaining members bootstrap from its resolved address.
 */
final class AtomixClusterEphemeralPortTest {
  @AutoClose private final MeterRegistry registry = new SimpleMeterRegistry();

  private final List<AtomixCluster> clusters = new ArrayList<>();

  @AfterEach
  void tearDown() {
    CloseHelper.quietCloseAll(clusters.stream().map(c -> (AutoCloseable) () -> c.stop().join()));
  }

  @Test
  void shouldAdvertiseResolvedPort() {
    // given
    final var cluster = createMember("single", List.of());

    // when
    cluster.start().join();

    // then
    final var localMember = cluster.getMembershipService().getLocalMember();
    assertThat(localMember.address().port())
        .isEqualTo(cluster.getMessagingService().address().port())
        .isPositive();
    // the unicast (UDP) service shares the port number, as members address unicast messages to
    // the single advertised member address
    assertThat(((NettyUnicastService) cluster.getUnicastService()).bindAddress().port())
        .isEqualTo(localMember.address().port());
  }

  @Test
  void shouldFormClusterWithEphemeralPorts() {
    // given - a seed member with an OS-assigned port
    final var seed = createMember("seed", List.of());
    seed.start().join();
    final var seedAddress = seed.getMessagingService().address();
    assertThat(seedAddress.port()).isPositive();

    // when - the remaining members bootstrap from the seed's resolved address
    final var member1 = createMember("member-1", List.of(seedAddress));
    final var member2 = createMember("member-2", List.of(seedAddress));
    member1.start().join();
    member2.start().join();

    // then - all members discover each other through the seed
    Awaitility.await("until all members know all other members")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () ->
                clusters.forEach(
                    cluster -> assertThat(cluster.getMembershipService().getMembers()).hasSize(3)));
  }

  private AtomixCluster createMember(final String memberId, final List<Address> seeds) {
    final var nodes =
        seeds.stream().map(address -> (Node) Node.builder().withAddress(address).build()).toList();
    final var cluster =
        AtomixCluster.builder(registry)
            .withClusterId("ephemeral-port-test")
            .withMemberId(memberId)
            .withHost("127.0.0.1")
            .withPort(0)
            .withMembershipProvider(BootstrapDiscoveryProvider.builder().withNodes(nodes).build())
            .build();
    clusters.add(cluster);
    return cluster;
  }
}
