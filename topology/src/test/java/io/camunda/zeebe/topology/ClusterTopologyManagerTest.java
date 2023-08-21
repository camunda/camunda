/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonParseException;
import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import io.camunda.zeebe.topology.state.ClusterTopology;
import io.camunda.zeebe.topology.state.MemberState;
import io.camunda.zeebe.topology.state.PartitionState;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ClusterTopologyManagerTest {

  AtomicReference<ClusterTopology> gossipState = new AtomicReference<>();
  private final Consumer<ClusterTopology> gossipHandler = gossipState::set;

  @Test
  void shouldInitializeClusterTopologyFromProvidedConfig(@TempDir final Path topologyFile) {
    // given
    final ClusterTopologyManager clusterTopologyManager =
        new ClusterTopologyManager(
            new TestConcurrencyControl(),
            new PersistedClusterTopology(topologyFile.resolve("topology.temp")));
    clusterTopologyManager.setTopologyGossiper(gossipHandler);

    // when
    clusterTopologyManager.start(this::getPartitionDistribution).join();

    // then
    final ClusterTopology clusterTopology = clusterTopologyManager.getClusterTopology().join();
    ClusterTopologyAssert.assertThatClusterTopology(clusterTopology)
        .hasMemberWithPartitions(0, Set.of(1));
  }

  @Test
  void shouldGossipInitialTopology(@TempDir final Path topologyFile) {
    // given
    final ClusterTopologyManager clusterTopologyManager =
        new ClusterTopologyManager(
            new TestConcurrencyControl(),
            new PersistedClusterTopology(topologyFile.resolve("topology.temp")));
    clusterTopologyManager.setTopologyGossiper(gossipHandler);

    // when
    clusterTopologyManager.start(this::getPartitionDistribution).join();

    // then
    final ClusterTopology gossippedTopology = gossipState.get();
    ClusterTopologyAssert.assertThatClusterTopology(gossippedTopology)
        .hasOnlyMembers(Set.of(0, 1, 2));
  }

  private Set<PartitionMetadata> getPartitionDistribution() {
    final var members =
        IntStream.range(0, 3)
            .mapToObj(i -> MemberId.from(String.valueOf(i)))
            .collect(Collectors.toSet());
    final var priorities =
        members.stream().collect(Collectors.toMap(m -> m, m -> Integer.valueOf(m.id()) + 1));
    return Set.of(
        new PartitionMetadata(
            PartitionId.from("test", 1), members, priorities, 1, MemberId.from("0")));
  }

  @Test
  void shouldInitializeClusterTopologyFromFile(@TempDir final Path topologyFile)
      throws IOException {
    // given
    final Path existingTopologyFile = topologyFile.resolve("topology.temp");
    final var existingTopology =
        ClusterTopology.init()
            .addMember(
                MemberId.from("5"),
                MemberState.initializeAsActive(Map.of(10, PartitionState.active(4))));
    Files.write(existingTopologyFile, existingTopology.encode());
    final ClusterTopologyManager clusterTopologyManager =
        new ClusterTopologyManager(
            new TestConcurrencyControl(), new PersistedClusterTopology(existingTopologyFile));
    clusterTopologyManager.setTopologyGossiper(gossipHandler);

    // when
    clusterTopologyManager.start(this::getPartitionDistribution).join();

    // then
    final ClusterTopology clusterTopology = clusterTopologyManager.getClusterTopology().join();
    ClusterTopologyAssert.assertThatClusterTopology(clusterTopology)
        .hasOnlyMembers(Set.of(5))
        .hasMemberWithPartitions(5, Set.of(10));
  }

  @Test
  void shouldFailIfTopologyFileIsCorrupted(@TempDir final Path topologyFile) throws IOException {
    // given
    final Path existingTopologyFile = topologyFile.resolve("topology.temp");
    Files.write(existingTopologyFile, new byte[10]); // write random string
    final ClusterTopologyManager clusterTopologyManager =
        new ClusterTopologyManager(
            new TestConcurrencyControl(), new PersistedClusterTopology(existingTopologyFile));

    // when - then
    assertThat(clusterTopologyManager.start(this::getPartitionDistribution))
        .failsWithin(Duration.ofMillis(100))
        .withThrowableThat()
        .withCauseInstanceOf(JsonParseException.class);
  }

  @Test
  void shouldUpdateLocalTopologyOnGossipEvent(@TempDir final Path topologyFile) {
    // given
    final ClusterTopologyManager clusterTopologyManager =
        new ClusterTopologyManager(
            new TestConcurrencyControl(),
            new PersistedClusterTopology(topologyFile.resolve("topology.temp")));
    clusterTopologyManager.setTopologyGossiper(gossipHandler);

    clusterTopologyManager.start(this::getPartitionDistribution).join();

    // when
    final ClusterTopology topologyFromOtherMember =
        clusterTopologyManager
            .getClusterTopology()
            .join()
            .addMember(MemberId.from("10"), MemberState.initializeAsActive(Map.of()));
    final var gossipedTopology =
        clusterTopologyManager.onGossipReceived(topologyFromOtherMember).join();

    // then
    final ClusterTopology clusterTopology = clusterTopologyManager.getClusterTopology().join();
    ClusterTopologyAssert.assertThatClusterTopology(clusterTopology)
        .hasOnlyMembers(Set.of(0, 1, 2, 10));
    assertThat(gossipedTopology)
        .describedAs("Gossip state contains same topology in topology manager")
        .isEqualTo(clusterTopology);
  }
}
