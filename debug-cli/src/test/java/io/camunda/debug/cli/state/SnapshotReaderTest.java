/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.debug.cli.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.protocol.ZbColumnFamilies;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SnapshotReaderTest {

  @TempDir Path tempDir;

  @Test
  void shouldReadFromCopiedRuntimeAndLeaveExplicitRuntimeAvailable() throws Exception {
    // given
    final var partitionRoot = tempDir.resolve("partition");
    try (final var db =
        SnapshotTestUtil.newDbFactory().createDb(tempDir.resolve("initial").toFile())) {
      final var snapshot = new SnapshotUtil().takeSnapshot(db, partitionRoot, "1-1-1-1-1", 1L);
      final var runtime = tempDir.resolve("runtime");

      // when
      final var isEmpty =
          SnapshotReader.read(
              partitionRoot,
              snapshot.getId().toString(),
              runtime,
              openedDb -> openedDb.isEmpty(ZbColumnFamilies.DEFAULT, openedDb.createContext()));

      // then
      assertThat(isEmpty).isTrue();
      assertThat(runtime).isDirectory();
    }
  }

  @Test
  void shouldRejectMissingSnapshot() {
    assertThatThrownBy(
            () ->
                SnapshotReader.read(tempDir.resolve("partition"), "missing", null, ignored -> null))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("Snapshot directory does not exist");
  }
}
