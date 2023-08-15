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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ClusterTopologyManagerTest {

  @Test
  void shouldInitializeClusterTopologyFromProvidedConfig(@TempDir final Path topologyFile) {
    // given
    final ClusterTopologyManager clusterTopologyManager =
        new ClusterTopologyManager(
            new TestConcurrencyControl(),
            new PersistedClusterTopology(topologyFile.resolve("topology.temp")));

    // when
    clusterTopologyManager.start(this::getPartitionDistribution).join();

    // then
    final ClusterTopology clusterTopology = clusterTopologyManager.getClusterTopology().join();
    ClusterTopologyAssert.assertThatClusterTopology(clusterTopology)
        .hasMemberWithPartitions(0, Set.of(1))
        .hasMemberWithPartitions(1, Set.of(2))
        .hasMemberWithPartitions(2, Set.of(3));
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
}
