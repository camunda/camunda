/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.snapshots.impl;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

final class FileBasedSnapshotIdTest {
  @Test
  void canParseIdWithoutChecksum() {
    // given
    final var id = "1-2-3-4-5";

    // when
    final var snapshotId = FileBasedSnapshotId.ofFileName(id).getOrThrow();

    // then
    assertEquals(1, snapshotId.getIndex());
    assertEquals(2, snapshotId.getTerm());
    assertEquals(3, snapshotId.getProcessedPosition());
    assertEquals(4, snapshotId.getExportedPosition());
  }

  @Test
  void canRoundTripWithoutChecksum() {
    // given
    final var originalId = "1-2-3-4-5";

    // when
    final var parsedId = FileBasedSnapshotId.ofFileName(originalId).getOrThrow();

    // then
    assertEquals(originalId, parsedId.getSnapshotIdAsString());
  }

  @Test
  void canParseIdWithChecksum() {
    // given
    final var id = "1-2-3-4-5-somechecksum";

    // when
    final var snapshotId = FileBasedSnapshotId.ofFileName(id).getOrThrow();
    // then
    assertEquals(1, snapshotId.getIndex());
    assertEquals(2, snapshotId.getTerm());
    assertEquals(3, snapshotId.getProcessedPosition());
    assertEquals(4, snapshotId.getExportedPosition());
  }

  @Test
  void canRoundTripWithChecksum() {
    // given
    final var originalId = "1-2-3-4-5-somechecksum";

    // when
    final var parsedId = FileBasedSnapshotId.ofFileName(originalId).getOrThrow();

    // then
    assertEquals(originalId, parsedId.getSnapshotIdAsString());
  }
}
