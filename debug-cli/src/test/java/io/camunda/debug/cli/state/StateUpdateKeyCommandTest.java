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
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.stream.impl.state.DbKeyGenerator;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import picocli.CommandLine;

class StateUpdateKeyCommandTest {

  CommandLine commandLine;
  @TempDir Path tempDir;
  StringWriter err;
  StringWriter out;

  @BeforeEach
  public void setup() {
    err = new StringWriter();
    out = new StringWriter();
    commandLine =
        new CommandLine(new Main()).setErr(new PrintWriter(err)).setOut(new PrintWriter(out));
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
    final var newSnapshotPath =
        SnapshotTestUtil.newSnapshotPath(partitionRoot, initialSnapshot.getId());

    final var snapshotUtil = new SnapshotUtil();
    final var runtimeDb =
        snapshotUtil.openSnapshot(newSnapshotPath, tempDir.resolve("openedRuntime"));
    final var keyGen = new DbKeyGenerator(1, runtimeDb, runtimeDb.createContext());
    final var key = keyGen.nextKey();
    assertThat(key).isEqualTo(resetKey + 1);
    assertThat(keyGen.getMaxKeyValue()).isEqualTo(maxKey);
  }

  private PersistedSnapshot takeInitialSnapshot(final Path partitionRoot) {
    try (final var initialRuntime =
        SnapshotTestUtil.newDbFactory().createDb(tempDir.resolve("initialRuntime").toFile())) {

      final var keyGen = new DbKeyGenerator(1, initialRuntime, initialRuntime.createContext());

      assertThat(keyGen.nextKey()).isEqualTo(Protocol.encodePartitionId(1, 1L));

      return new SnapshotUtil().takeSnapshot(initialRuntime, partitionRoot, "1-1-1-1-1", 1L);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"root", "partition-id", "key", "snapshot", "runtime"})
  void shouldFailIfRequiredArgumentIsNotSet(final String optionToSkip) {
    // given
    final var options = new ArrayList<>(List.of("state", "update-key", "-v"));
    final var requiredOptions =
        List.of(
            "--root=rootFolder",
            "--partition-id=1",
            "--key=" + 100,
            "--max-key=" + 1000,
            "--snapshot=" + "exampleId",
            "--runtime=" + tempDir.resolve("runtime"));

    options.addAll(
        requiredOptions.stream()
            // skip the option if matches
            .filter(o -> !o.split("=")[0].equals("--" + optionToSkip))
            .toList());

    // when
    final int exitCode = commandLine.execute(options.toArray(String[]::new));

    // then
    assertThat(exitCode).isPositive();
    assertThat(err.toString()).contains("Missing required option: '--%s=".formatted(optionToSkip));
  }
}
