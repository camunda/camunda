/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationManagerService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RestoreValidatorTest {

  @TempDir Path dataDirectory;

  private BrokerCfg brokerCfg;

  @BeforeEach
  void setUp() {
    brokerCfg = new BrokerCfg();
    brokerCfg.getData().setDirectory(dataDirectory.toString());
    brokerCfg.getCluster().setNodeId(0);
    brokerCfg.getCluster().setClusterSize(1);
    brokerCfg.getCluster().setPartitionsCount(1);
    brokerCfg.getCluster().setReplicationFactor(1);
  }

  @Test
  void shouldReturnFalseWhenPartitionDirectoryDoesNotExist() {
    // given - no partition directory created

    // when
    final boolean result = RestoreValidator.validate(brokerCfg);

    // then
    assertThat(result).isFalse();
  }

  @Test
  void shouldReturnFalseWhenPartitionDirectoryIsEmpty() throws IOException {
    // given
    final Path partitionDir = getPartitionDir();
    Files.createDirectories(partitionDir);

    // when
    final boolean result = RestoreValidator.validate(brokerCfg);

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
    final boolean result = RestoreValidator.validate(brokerCfg);

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
    final boolean result = RestoreValidator.validate(brokerCfg);

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
    final boolean result = RestoreValidator.validate(brokerCfg);

    // then
    assertThat(result).isFalse();
  }

  @Test
  void shouldReturnTrueWithMultiplePartitions() throws IOException {
    // given
    brokerCfg.getCluster().setPartitionsCount(3);

    for (int i = 1; i <= 3; i++) {
      final Path partitionDir = getPartitionDir(i);
      Files.createDirectories(partitionDir);
      Files.writeString(partitionDir.resolve("segment-1.log"), "data");
    }

    final Path topologyFile =
        dataDirectory.resolve(ClusterConfigurationManagerService.TOPOLOGY_FILE_NAME);
    Files.writeString(topologyFile, "topology");

    // when
    final boolean result = RestoreValidator.validate(brokerCfg);

    // then
    assertThat(result).isTrue();
  }

  @Test
  void shouldReturnFalseWhenOneOfMultiplePartitionsIsEmpty() throws IOException {
    // given
    brokerCfg.getCluster().setPartitionsCount(3);

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
    final boolean result = RestoreValidator.validate(brokerCfg);

    // then
    assertThat(result).isFalse();
  }

  private Path getPartitionDir() {
    return getPartitionDir(1);
  }

  private Path getPartitionDir(final int partitionId) {
    return dataDirectory
        .resolve("raft-partition")
        .resolve("partitions")
        .resolve(String.valueOf(partitionId));
  }
}
