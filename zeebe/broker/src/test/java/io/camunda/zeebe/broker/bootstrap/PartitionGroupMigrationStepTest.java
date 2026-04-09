/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

class PartitionGroupMigrationStepTest {

  private static final TestConcurrencyControl CONCURRENCY_CONTROL = new TestConcurrencyControl();
  private static final Duration TIME_OUT = Duration.ofSeconds(10);

  @TempDir private Path dataDir;

  private BrokerStartupContext mockContext;
  private final PartitionGroupMigrationStep sut = new PartitionGroupMigrationStep();

  @BeforeEach
  void setUp() {
    final var brokerCfg = new BrokerCfg();
    brokerCfg.getData().setDirectory(dataDir.toString());

    mockContext = Mockito.mock(BrokerStartupContext.class);
    Mockito.when(mockContext.getBrokerConfiguration()).thenReturn(brokerCfg);
    Mockito.when(mockContext.getConcurrencyControl()).thenReturn(CONCURRENCY_CONTROL);
  }

  @Test
  void shouldCompleteOnFreshInstall() {
    // given — neither raft-partition nor default directory exists

    // when
    final var future = sut.startup(mockContext);

    // then
    assertThat(future).succeedsWithin(TIME_OUT);
    assertThat(dataDir.resolve("raft-partition")).doesNotExist();
    assertThat(dataDir.resolve("default")).doesNotExist();
  }

  @Test
  void shouldCompleteWhenAlreadyMigrated() throws Exception {
    // given — only the new directory exists
    Files.createDirectories(dataDir.resolve("default/partitions/1"));

    // when
    final var future = sut.startup(mockContext);

    // then
    assertThat(future).succeedsWithin(TIME_OUT);
    assertThat(dataDir.resolve("default/partitions/1")).exists();
    assertThat(dataDir.resolve("raft-partition")).doesNotExist();
  }

  @Test
  void shouldFailWhenBothDirectoriesExist() throws Exception {
    // given — both old and new directories exist
    Files.createDirectories(dataDir.resolve("raft-partition/partitions/1"));
    Files.createDirectories(dataDir.resolve("default/partitions/1"));

    // when
    final var future = sut.startup(mockContext);

    // then
    assertThat(future).failsWithin(TIME_OUT);
  }

  @Test
  void shouldMigratePartitionFiles() throws Exception {
    // given — old directory with all file types for partition 1
    final var partitionDir = dataDir.resolve("raft-partition/partitions/1");
    Files.createDirectories(partitionDir);
    Files.createFile(partitionDir.resolve("raft-partition-partition-1.meta"));
    Files.createFile(partitionDir.resolve("raft-partition-partition-1.conf"));
    Files.createFile(partitionDir.resolve(".raft-partition-partition-1.lock"));
    Files.createFile(partitionDir.resolve("raft-partition-partition-1-1.log"));
    Files.createFile(partitionDir.resolve("raft-partition-partition-1-2.log"));
    Files.createFile(partitionDir.resolve("raft-partition-partition-1-1.log_0-deleted"));
    // a snapshot file that should NOT be renamed
    Files.createFile(partitionDir.resolve("snapshot-metadata.properties"));

    // when
    final var future = sut.startup(mockContext);

    // then
    assertThat(future).succeedsWithin(TIME_OUT);
    assertThat(dataDir.resolve("raft-partition")).doesNotExist();

    final var migratedDir = dataDir.resolve("default/partitions/1");
    assertThat(migratedDir).exists();
    assertThat(migratedDir.resolve("default-partition-1.meta")).exists();
    assertThat(migratedDir.resolve("default-partition-1.conf")).exists();
    assertThat(migratedDir.resolve(".default-partition-1.lock")).exists();
    assertThat(migratedDir.resolve("default-partition-1-1.log")).exists();
    assertThat(migratedDir.resolve("default-partition-1-2.log")).exists();
    assertThat(migratedDir.resolve("default-partition-1-1.log_0-deleted")).exists();
    // snapshot file should be unchanged
    assertThat(migratedDir.resolve("snapshot-metadata.properties")).exists();
  }

  @Test
  void shouldMigrateMultiplePartitions() throws Exception {
    // given — two partitions with meta and conf files
    for (final int id : new int[] {1, 2}) {
      final var partitionDir = dataDir.resolve("raft-partition/partitions/" + id);
      Files.createDirectories(partitionDir);
      Files.createFile(partitionDir.resolve("raft-partition-partition-" + id + ".meta"));
      Files.createFile(partitionDir.resolve("raft-partition-partition-" + id + ".conf"));
      Files.createFile(partitionDir.resolve("raft-partition-partition-" + id + "-1.log"));
    }

    // when
    final var future = sut.startup(mockContext);

    // then
    assertThat(future).succeedsWithin(TIME_OUT);
    assertThat(dataDir.resolve("raft-partition")).doesNotExist();

    for (final int id : new int[] {1, 2}) {
      final var migratedDir = dataDir.resolve("default/partitions/" + id);
      assertThat(migratedDir.resolve("default-partition-" + id + ".meta")).exists();
      assertThat(migratedDir.resolve("default-partition-" + id + ".conf")).exists();
      assertThat(migratedDir.resolve("default-partition-" + id + "-1.log")).exists();
    }
  }

  @Test
  void shouldBeIdempotent() throws Exception {
    // given — old directory with a partition
    final var partitionDir = dataDir.resolve("raft-partition/partitions/1");
    Files.createDirectories(partitionDir);
    Files.createFile(partitionDir.resolve("raft-partition-partition-1.meta"));
    Files.createFile(partitionDir.resolve("raft-partition-partition-1.conf"));

    // when — migrate twice
    final var first = sut.startup(mockContext);
    assertThat(first).succeedsWithin(TIME_OUT);
    final var second = sut.startup(mockContext);

    // then — second run is a no-op
    assertThat(second).succeedsWithin(TIME_OUT);
    final var migratedDir = dataDir.resolve("default/partitions/1");
    assertThat(migratedDir.resolve("default-partition-1.meta")).exists();
    assertThat(migratedDir.resolve("default-partition-1.conf")).exists();
  }

  @Test
  void shouldHandlePartiallyRenamedFiles() throws Exception {
    // given — some files already renamed, some not
    final var partitionDir = dataDir.resolve("raft-partition/partitions/1");
    Files.createDirectories(partitionDir);
    // already renamed
    Files.createFile(partitionDir.resolve("default-partition-1.meta"));
    // not yet renamed
    Files.createFile(partitionDir.resolve("raft-partition-partition-1.conf"));
    Files.createFile(partitionDir.resolve("raft-partition-partition-1-1.log"));

    // when
    final var future = sut.startup(mockContext);

    // then
    assertThat(future).succeedsWithin(TIME_OUT);
    final var migratedDir = dataDir.resolve("default/partitions/1");
    assertThat(migratedDir.resolve("default-partition-1.meta")).exists();
    assertThat(migratedDir.resolve("default-partition-1.conf")).exists();
    assertThat(migratedDir.resolve("default-partition-1-1.log")).exists();
  }

  @Test
  void shouldLeaveNoLegacyPrefixedFiles() throws Exception {
    // given — two partitions populated with the full set of file types
    for (final int id : new int[] {1, 2}) {
      final var partitionDir = dataDir.resolve("raft-partition/partitions/" + id);
      Files.createDirectories(partitionDir);
      Files.createFile(partitionDir.resolve("raft-partition-partition-" + id + ".meta"));
      Files.createFile(partitionDir.resolve("raft-partition-partition-" + id + ".conf"));
      Files.createFile(partitionDir.resolve(".raft-partition-partition-" + id + ".lock"));
      Files.createFile(partitionDir.resolve(".raft-partition-partition-" + id + ".lock.tmp"));
      Files.createFile(partitionDir.resolve("raft-partition-partition-" + id + "-1.log"));
      Files.createFile(partitionDir.resolve("raft-partition-partition-" + id + "-1.log_0-deleted"));
    }

    // when
    final var future = sut.startup(mockContext);
    assertThat(future).succeedsWithin(TIME_OUT);

    // then — no file anywhere under the data directory carries the legacy prefix
    try (final Stream<Path> tree = Files.walk(dataDir)) {
      assertThat(tree)
          .filteredOn(Files::isRegularFile)
          .allSatisfy(
              path ->
                  assertThat(path.getFileName().toString())
                      .as("File %s should not carry the legacy 'raft-partition' prefix", path)
                      .doesNotContain("raft-partition"));
    }
  }

  @Test
  void shouldCompleteShutdown() {
    // when
    final var future = sut.shutdown(mockContext);

    // then
    assertThat(future).succeedsWithin(TIME_OUT);
  }
}
