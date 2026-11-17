/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.startup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ZeebePartitionFactoryTest {

  @Test
  void shouldAppendPartitionGroupToSeparateRuntimeDirectory(@TempDir final Path runtimeRoot) {
    // given a broker configured with a separate root runtime directory
    final var brokerCfg = new BrokerCfg();
    brokerCfg.getData().setRuntimeDirectory(runtimeRoot.toString());
    final var partitionId = new PartitionId("tenanta", 1);

    // when
    final Path resolved =
        ZeebePartitionFactory.resolveRuntimeDirectory(brokerCfg, partitionId, runtimeRoot.toFile());

    // then the partition group is part of the resolved path
    assertThat(resolved).isEqualTo(runtimeRoot.resolve("tenanta").resolve("1"));
  }

  @Test
  void shouldNotCollideForDifferentPartitionGroupsWithTheSamePartitionNumber(
      @TempDir final Path runtimeRoot) {
    // given two physical tenants (partition groups) that do not override the runtime directory,
    // so both resolve the same root runtime directory from the shared BrokerCfg, and partition
    // numbers restart at 1 per group
    final var brokerCfg = new BrokerCfg();
    brokerCfg.getData().setRuntimeDirectory(runtimeRoot.toString());
    final var tenantAPartitionOne = new PartitionId("tenanta", 1);
    final var tenantBPartitionOne = new PartitionId("tenantb", 1);

    // when
    final Path tenantARuntimeDirectory =
        ZeebePartitionFactory.resolveRuntimeDirectory(
            brokerCfg, tenantAPartitionOne, runtimeRoot.toFile());
    final Path tenantBRuntimeDirectory =
        ZeebePartitionFactory.resolveRuntimeDirectory(
            brokerCfg, tenantBPartitionOne, runtimeRoot.toFile());

    // then each tenant's same-numbered partition gets a distinct runtime directory
    assertThat(tenantARuntimeDirectory).isNotEqualTo(tenantBRuntimeDirectory);
  }

  @Test
  void shouldCreateTheGroupScopedRuntimeRootDirectory(@TempDir final Path runtimeRoot) {
    // given a broker configured with a separate root runtime directory
    final var brokerCfg = new BrokerCfg();
    brokerCfg.getData().setRuntimeDirectory(runtimeRoot.toString());
    final var partitionId = new PartitionId("tenanta", 2);

    // when
    ZeebePartitionFactory.resolveRuntimeDirectory(brokerCfg, partitionId, runtimeRoot.toFile());

    // then the group-scoped parent directory was created on disk
    assertThat(runtimeRoot.resolve("tenanta")).isDirectory();
  }

  @Test
  void shouldResolveRuntimeDirectoryUnderPartitionDataDirectoryWhenNotUsingSeparateDirectory(
      @TempDir final Path partitionDataDirectory) {
    // given a broker that does not configure a separate runtime directory
    final var brokerCfg = new BrokerCfg();
    final var partitionId = new PartitionId("default", 3);

    // when
    final Path resolved =
        ZeebePartitionFactory.resolveRuntimeDirectory(
            brokerCfg, partitionId, partitionDataDirectory.toFile());

    // then it falls back to a fixed subdirectory of the partition's own (already
    // group-scoped) data directory, unaffected by this fix
    assertThat(resolved)
        .isEqualTo(partitionDataDirectory.resolve(ZeebePartitionFactory.DEFAULT_RUNTIME_DIRECTORY));
  }

  @Test
  void shouldThrowWhenRuntimeDirectoryCannotBeCreated(@TempDir final Path runtimeRoot) {
    // given a root runtime directory path that is actually a regular file, so it cannot be
    // turned into a directory
    final File blockingFile = runtimeRoot.resolve("not-a-directory").toFile();
    final var brokerCfg = new BrokerCfg();
    brokerCfg.getData().setRuntimeDirectory(blockingFile.getPath());
    final var partitionId = new PartitionId("tenanta", 1);

    try {
      assertThat(blockingFile.createNewFile()).isTrue();
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }

    // when / then
    assertThat(
            catchThrowable(
                () ->
                    ZeebePartitionFactory.resolveRuntimeDirectory(
                        brokerCfg, partitionId, runtimeRoot.toFile())))
        .isInstanceOf(UncheckedIOException.class);
  }
}
