/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore.validation;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationManagerService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PostRestoreValidatorTest {
  @TempDir Path dataDirectory;

  private PostRestoreValidator postRestoreValidator;

  @BeforeEach
  void setUp() {
    postRestoreValidator = getPostRestoreValidator(1);
  }

  @Test
  void shouldReturnFalseWhenPartitionDirectoryDoesNotExist() {
    // given - no partition directory created

    // when
    final boolean result = postRestoreValidator.verifyRestore();

    // then
    assertThat(result).isFalse();
  }

  @Test
  void shouldReturnFalseWhenPartitionDirectoryIsEmpty() throws IOException {
    // given
    final Path partitionDir = getPartitionDir();
    Files.createDirectories(partitionDir);

    // when
    final boolean result = postRestoreValidator.verifyRestore();

    // then
    assertThat(result).isFalse();
  }

  @Test
  void shouldReturnFalseWhenTopologyFileIsMissing() throws IOException {
    // given
    final Path partitionDir = getPartitionDir();
    Files.createDirectories(partitionDir);
    Files.writeString(partitionDir.resolve("segment-1.log"), "data");

    // when
    final boolean result = postRestoreValidator.verifyRestore();

    // then
    assertThat(result).isFalse();
  }

  @Test
  void shouldReturnTrueWhenPartitionDirectoryAndTopologyFileExist() throws IOException {
    // given
    final Path partitionDir = getPartitionDir();
    Files.createDirectories(partitionDir);
    Files.writeString(partitionDir.resolve("segment-1.log"), "data");

    final Path topologyFile =
        dataDirectory.resolve(ClusterConfigurationManagerService.TOPOLOGY_FILE_NAME);
    Files.writeString(topologyFile, "topology");

    // when
    final boolean result = postRestoreValidator.verifyRestore();

    // then
    assertThat(result).isTrue();
  }

  @Test
  void shouldReturnFalseWhenTopologyFileIsDirectory() throws IOException {
    // given
    final Path partitionDir = getPartitionDir();
    Files.createDirectories(partitionDir);
    Files.writeString(partitionDir.resolve("segment-1.log"), "data");

    final Path topologyDir =
        dataDirectory.resolve(ClusterConfigurationManagerService.TOPOLOGY_FILE_NAME);
    Files.createDirectories(topologyDir);

    // when
    final boolean result = postRestoreValidator.verifyRestore();

    // then
    assertThat(result).isFalse();
  }

  @Test
  void shouldReturnTrueWithMultiplePartitions() throws IOException {
    // given
    postRestoreValidator = getPostRestoreValidator(3);

    for (int i = 1; i <= 3; i++) {
      final Path partitionDir = getPartitionDir(i);
      Files.createDirectories(partitionDir);
      Files.writeString(partitionDir.resolve("segment-1.log"), "data");
    }

    final Path topologyFile =
        dataDirectory.resolve(ClusterConfigurationManagerService.TOPOLOGY_FILE_NAME);
    Files.writeString(topologyFile, "topology");

    // when
    final boolean result = postRestoreValidator.verifyRestore();

    // then
    assertThat(result).isTrue();
  }

  @Test
  void shouldReturnFalseWhenOneOfMultiplePartitionsIsEmpty() throws IOException {
    // given
    postRestoreValidator = getPostRestoreValidator(3);

    for (int i = 1; i <= 3; i++) {
      final Path partitionDir = getPartitionDir(i);
      Files.createDirectories(partitionDir);
      if (i < 3) {
        Files.writeString(partitionDir.resolve("segment-1.log"), "data");
      }
    }

    final Path topologyFile =
        dataDirectory.resolve(ClusterConfigurationManagerService.TOPOLOGY_FILE_NAME);
    Files.writeString(topologyFile, "topology");

    // when
    final boolean result = postRestoreValidator.verifyRestore();

    // then
    assertThat(result).isFalse();
  }

  private PostRestoreValidator getPostRestoreValidator(final int partitionCount) {
    final var memberId = MemberId.from(0);
    final var partitionMap = new HashMap<PartitionMetadata, Path>();
    for (int i = 1; i <= partitionCount; i++) {
      final var partitionId = new PartitionId("default", i);
      final var partitionMetadata =
          new PartitionMetadata(partitionId, Set.of(memberId), Map.of(), 0, memberId);
      partitionMap.put(partitionMetadata, getPartitionDir(i));
    }
    return new PostRestoreValidator(memberId, partitionMap, dataDirectory);
  }

  private Path getPartitionDir() {
    return getPartitionDir(1);
  }

  private Path getPartitionDir(final int partitionId) {
    return dataDirectory.resolve(String.valueOf(partitionId));
  }
}
