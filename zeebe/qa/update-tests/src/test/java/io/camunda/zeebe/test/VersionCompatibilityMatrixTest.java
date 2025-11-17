/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.camunda.zeebe.test.VersionCompatibilityMatrix.VersionProvider;
import io.camunda.zeebe.util.SemanticVersion;
import java.util.LinkedList;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class VersionCompatibilityMatrixTest {

  @Test
  void filterPreReleaseVersion() {
    final VersionCompatibilityMatrix matrix =
        new VersionCompatibilityMatrix(
            new StaticVersionProvider(
                Set.of("8.7.11", "8.8.2", "8.8.2-optimize"),
                Set.of("8.7.11", "8.8.2", "8.8.2-optimize")));

    final var versions =
        matrix.discoverVersions().map(SemanticVersion::toString).collect(Collectors.toSet());

    assertThat(versions).isEqualTo(Set.of("8.7.11", "8.8.2"));
  }

  @Test
  void filterUnreleasedLatestVersion() {
    final VersionCompatibilityMatrix matrix =
        new VersionCompatibilityMatrix(
            new StaticVersionProvider(
                Set.of("8.7.11", "8.8.0", "8.8.1", "8.8.2", "8.8.2-optimize"),
                Set.of("8.7.11", "8.8.0", "8.8.1")));

    final var versions =
        matrix.discoverVersions().map(SemanticVersion::toString).collect(Collectors.toSet());

    assertThat(versions).isEqualTo(Set.of("8.7.11", "8.8.0", "8.8.1"));
  }

  @ParameterizedTest(name = "Sharding {0} elements into {1} shards")
  @MethodSource("sizeAndShards")
  void shouldShardCompletely(final int size, final int totalShards) {
    final var input = IntStream.range(0, size).boxed().toList();
    final var sharded = new LinkedList<Integer>();
    for (int i = 0; i < totalShards; i++) {
      final var shard = VersionCompatibilityMatrix.shard(input, i, totalShards).toList();
      assertThat(shard).isNotEmpty();
      sharded.addAll(shard);
    }
    assertThat(sharded).isEqualTo(input);
  }

  static Stream<Arguments> sizeAndShards() {
    return IntStream.rangeClosed(0, 10)
        .boxed()
        .flatMap(
            size ->
                IntStream.rangeClosed(1, 10)
                    .boxed()
                    .filter(shards -> shards < size)
                    .map(shards -> arguments(size, shards)));
  }

  static class StaticVersionProvider implements VersionProvider {

    private final Set<SemanticVersion> versions;
    private final Set<SemanticVersion> releasedVersions;

    public StaticVersionProvider(final Set<String> versions, final Set<String> releasedVersions) {
      this.versions =
          versions.stream()
              .map(SemanticVersion::parse)
              .flatMap(Optional::stream)
              .collect(Collectors.toSet());
      this.releasedVersions =
          releasedVersions.stream()
              .map(SemanticVersion::parse)
              .flatMap(Optional::stream)
              .collect(Collectors.toSet());
    }

    @Override
    public Stream<SemanticVersion> discoverVersions() {
      return versions.stream();
    }

    @Override
    public boolean isReleased(final SemanticVersion version) {
      return releasedVersions.contains(version);
    }
  }
}
