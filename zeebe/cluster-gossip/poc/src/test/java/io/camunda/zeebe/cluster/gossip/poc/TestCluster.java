/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.cluster.gossip.poc;

import io.atomix.cluster.AtomixCluster;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.Node;
import io.atomix.cluster.discovery.BootstrapDiscoveryProvider;
import io.atomix.cluster.protocol.SwimMembershipProtocol;
import io.camunda.zeebe.cluster.gossip.poc.core.AbstractClusterGossiper;
import io.camunda.zeebe.cluster.gossip.poc.swim.SwimClusterGossiper;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Real Atomix members with SWIM on localhost, each with a gossiper proof of concept on top. Members
 * are addressed by index; the member id of index {@code i} is {@code i}.
 */
final class TestCluster implements AutoCloseable {

  private final Transport transport;
  private final Timing timing;
  private final List<Node> nodes = new ArrayList<>();
  private final List<AtomixCluster> atomixMembers = new ArrayList<>();
  private final List<AbstractClusterGossiper> gossipers = new ArrayList<>();

  TestCluster(final Transport transport, final Timing timing, final int size) {
    this.transport = transport;
    this.timing = timing;
    for (int i = 0; i < size; i++) {
      nodes.add(
          Node.builder()
              .withId(String.valueOf(i))
              .withPort(SocketUtil.getNextAddress().getPort())
              .build());
      atomixMembers.add(null);
      gossipers.add(null);
    }
  }

  /** Starts every member. */
  TestCluster start() {
    for (int i = 0; i < nodes.size(); i++) {
      start(i);
    }
    return this;
  }

  /** Starts a new run of the member with the given index. */
  AbstractClusterGossiper start(final int index) {
    final var meterRegistry = new SimpleMeterRegistry();
    final var atomix =
        AtomixCluster.builder(meterRegistry)
            .withAddress(nodes.get(index).address())
            .withMemberId(nodes.get(index).id().id())
            .withMembershipProvider(new BootstrapDiscoveryProvider(nodes))
            .withMembershipProtocol(
                SwimMembershipProtocol.builder(meterRegistry)
                    .withGossipInterval(timing.gossipInterval())
                    .withSyncInterval(timing.syncInterval())
                    .withProbeInterval(timing.probeInterval())
                    .withFailureTimeout(timing.failureTimeout())
                    .build())
            .build();
    atomix.start().join();
    final var gossiper = transport.create(atomix, timing);
    gossiper.start();
    atomixMembers.set(index, atomix);
    gossipers.set(index, gossiper);
    return gossiper;
  }

  /** Stops the member with the given index. */
  void stop(final int index) {
    final var gossiper = gossipers.set(index, null);
    final var atomix = atomixMembers.set(index, null);
    if (gossiper != null) {
      gossiper.close();
    }
    if (atomix != null) {
      atomix.stop().join();
    }
  }

  /** Stops the member and starts a new, empty run of it on the same address. */
  AbstractClusterGossiper restart(final int index) {
    stop(index);
    return start(index);
  }

  AbstractClusterGossiper gossiper(final int index) {
    return gossipers.get(index);
  }

  /** The gossipers of all running members. */
  List<AbstractClusterGossiper> running() {
    return gossipers.stream().filter(gossiper -> gossiper != null).toList();
  }

  static MemberId memberId(final int index) {
    return MemberId.from(String.valueOf(index));
  }

  @Override
  public void close() {
    for (int i = 0; i < nodes.size(); i++) {
      stop(i);
    }
  }

  /** How state travels: which proof of concept runs on top of SWIM. */
  enum Transport {
    SWIM((atomix, timing) -> new SwimClusterGossiper(atomix.getMembershipService()));

    private final BiFunction<AtomixCluster, Timing, AbstractClusterGossiper> factory;

    Transport(final BiFunction<AtomixCluster, Timing, AbstractClusterGossiper> factory) {
      this.factory = factory;
    }

    AbstractClusterGossiper create(final AtomixCluster atomix, final Timing timing) {
      return factory.apply(atomix, timing);
    }
  }

  /**
   * Timing shared by SWIM and the anti-entropy transport, so both gossip at the same pace.
   *
   * @param gossipInterval SWIM gossip interval, and the anti-entropy push interval
   * @param syncInterval SWIM full sync interval, and the anti-entropy sync interval
   */
  record Timing(
      Duration gossipInterval,
      Duration syncInterval,
      Duration probeInterval,
      Duration failureTimeout) {

    /** Fast timing to keep tests short. */
    static Timing fast() {
      return new Timing(
          Duration.ofMillis(50),
          Duration.ofSeconds(1),
          Duration.ofMillis(100),
          Duration.ofSeconds(2));
    }

    /** The production defaults of SWIM, used to compare the transports realistically. */
    static Timing defaults() {
      return new Timing(
          Duration.ofMillis(250),
          Duration.ofSeconds(10),
          Duration.ofSeconds(1),
          Duration.ofSeconds(10));
    }
  }
}
