/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.debug.cli.state;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.debug.cli.Main;
import io.camunda.zeebe.db.AccessMetricsConfiguration;
import io.camunda.zeebe.db.AccessMetricsConfiguration.Kind;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStoreImpl;
import io.camunda.zeebe.stream.impl.state.DbKeyGenerator;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class StateUpdateKeyCommandTest {

  CommandLine commandLine;
  @TempDir Path tempDir;

  @BeforeEach
  public void setup() {
    commandLine = new CommandLine(new Main());
  }

  @Test
  void shouldUpdateKeyAndMaxKey() throws IOException {
    // given
    final Path partitionRoot = tempDir.resolve("partitionRoot");

    final var initialSnapshot = takeInitialSnapshot(partitionRoot);

    // when
    final var resetKey = Protocol.encodePartitionId(1, 100L);
    final var maxKey = resetKey + 1000;
    final int exitCode =
        commandLine.execute(
            "state",
            "update-key",
            "-v",
            "-r",
            partitionRoot.toString(),
            "--partition-id=1",
            "--key=" + resetKey,
            "--max-key=" + maxKey,
            "--snapshot=" + initialSnapshot.getId().toString(),
            "--runtime=" + tempDir.resolve("runtime"));

    // then
    assertThat(exitCode).isZero();

    // open the snapshot and verify the key
    // find the new snapshot id
    final var newSnapshotPath =
        Files.list(partitionRoot.resolve(FileBasedSnapshotStoreImpl.SNAPSHOTS_DIRECTORY))
            .filter(Files::isDirectory)
            .filter(
                directoryName ->
                    !directoryName.getFileName().toString().equals(initialSnapshot.getId()))
            .findFirst()
            .get();

    final var snapshotUtil = new SnapshotUtil();
    final var runtimeDb =
        snapshotUtil.openSnapshot(newSnapshotPath, tempDir.resolve("openedRuntime"));
    final var keyGen = new DbKeyGenerator(1, runtimeDb, runtimeDb.createContext());
    final var key = keyGen.nextKey();
    assertThat(key).isEqualTo(resetKey + 1);
    assertThat(keyGen.getMaxKeyValue()).isEqualTo(maxKey);
  }

  private PersistedSnapshot takeInitialSnapshot(final Path partitionRoot) {
    final var zeebeDbFactory =
        new ZeebeRocksDbFactory<>(
            new RocksDbConfiguration().setWalDisabled(false),
            new ConsistencyChecksSettings(true, true),
            new AccessMetricsConfiguration(Kind.NONE, 1),
            SimpleMeterRegistry::new);

    final var initialRuntime = zeebeDbFactory.createDb(tempDir.resolve("initialRuntime").toFile());

    final var keyGen = new DbKeyGenerator(1, initialRuntime, initialRuntime.createContext());

    assertThat(keyGen.nextKey()).isEqualTo(Protocol.encodePartitionId(1, 1L));

    return new SnapshotUtil().takeSnapshot(initialRuntime, partitionRoot, "1-1-1-1-1", 1L);
  }
}
