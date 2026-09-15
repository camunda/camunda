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
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class StateSummaryCommandTest {

  @TempDir Path tempDir;

  @Test
  void shouldPrintKnownColumnFamilyCountsAsJson() {
    // given
    final var partitionRoot = tempDir.resolve("partition");
    try (final var db =
        SnapshotTestUtil.newDbFactory().createDb(tempDir.resolve("initial").toFile())) {
      final var snapshot = new SnapshotUtil().takeSnapshot(db, partitionRoot, "1-1-1-1-1", 1L);
      final var output = new StringWriter();
      final var commandLine =
          new CommandLine(new Main())
              .setOut(new PrintWriter(output))
              .setErr(new PrintWriter(new StringWriter()));

      // when
      final var exitCode =
          commandLine.execute(
              "state",
              "summary",
              "--root",
              partitionRoot.toString(),
              "--snapshot",
              snapshot.getId().toString());

      // then
      assertThat(exitCode).isZero();
      assertThat(output.toString())
          .contains("\"snapshot\":\"" + snapshot.getId() + "\"")
          .contains("\"name\":\"DEFAULT\"")
          .contains("\"entries\":0");
    }
  }
}
