/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.cluster.gossip.swim;

import io.atomix.cluster.AtomixCluster;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.Node;
import io.atomix.cluster.discovery.BootstrapDiscoveryProvider;
import io.atomix.cluster.protocol.SwimMembershipProtocol;
import io.camunda.zeebe.cluster.gossip.testkit.contract.GossipTestCluster;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.awaitility.Awaitility;

/** A cluster of Atomix members with SWIM on localhost, each with its own gossiper. */
final class SwimTestCluster implements GossipTestCluster {

  private final List<Node> nodes = new ArrayList<>();
  private final List<AtomixCluster> atomixMembers = new ArrayList<>();
  private final List<SwimClusterGossiper> gossipers = new ArrayList<>();

  SwimTestCluster(final int size) {
    for (int i = 0; i < size; i++) {
      nodes.add(
          Node.builder()
              .withId(String.valueOf(i))
              .withPort(SocketUtil.getNextAddress().getPort())
              .build());
      atomixMembers.add(null);
      gossipers.add(null);
    }
    for (int i = 0; i < size; i++) {
      start(i);
    }
  }

  @Override
  public SwimClusterGossiper gossiper(final int index) {
    return gossipers.get(index);
  }

  /** The live properties of the member's own SWIM member. */
  Properties localProperties(final int index) {
    return atomixMembers.get(index).getMembershipService().getLocalMember().properties();
  }

  @Override
  public MemberId memberId(final int index) {
    return MemberId.from(String.valueOf(index));
  }

  @Override
  public void stop(final int index) {
    final var gossiper = gossipers.set(index, null);
    final var atomix = atomixMembers.set(index, null);
    if (gossiper != null) {
      gossiper.close();
    }
    if (atomix != null) {
      atomix.stop().join();
    }
  }

  @Override
  public SwimClusterGossiper restart(final int index) {
    stop(index);
    start(index);
    return gossiper(index);
  }

  @Override
  public void awaitAsserted(final Runnable assertion) {
    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofMillis(20))
        .untilAsserted(assertion::run);
  }

  @Override
  public boolean supportsGlobalKeys() {
    return false;
  }

  @Override
  public void close() {
    for (int i = 0; i < nodes.size(); i++) {
      stop(i);
    }
  }

  private void start(final int index) {
    final var meterRegistry = new SimpleMeterRegistry();
    final var atomix =
        AtomixCluster.builder(meterRegistry)
            .withAddress(nodes.get(index).address())
            .withMemberId(nodes.get(index).id().id())
            .withMembershipProvider(new BootstrapDiscoveryProvider(nodes))
            .withMembershipProtocol(
                SwimMembershipProtocol.builder(meterRegistry)
                    .withGossipInterval(Duration.ofMillis(50))
                    .withProbeInterval(Duration.ofMillis(100))
                    .withSyncInterval(Duration.ofSeconds(1))
                    .withFailureTimeout(Duration.ofSeconds(2))
                    .build())
            .build();
    atomix.start().join();
    final var gossiper = new SwimClusterGossiper(atomix.getMembershipService());
    gossiper.start();
    atomixMembers.set(index, atomix);
    gossipers.set(index, gossiper);
  }
}
