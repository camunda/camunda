/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.snapshots;

import static java.nio.file.StandardOpenOption.CREATE_NEW;

import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

public final class SnapshotTransferUtil {

  public static final Map<String, String> SNAPSHOT_FILE_CONTENTS =
      Map.of(
          "file1", "file1 contents",
          "file2", "file2 contents");

  public static ActorFuture<PersistedSnapshot> takePersistedSnapshot(
      final ConstructableSnapshotStore senderSnapshotStore,
      final Map<String, String> snapshotFileContents,
      final ConcurrencyControl control) {
    final var transientSnapshot =
        senderSnapshotStore.newTransientSnapshot(1L, 0L, 1, 0, false).get();
    return transientSnapshot
        .take(p -> writeSnapshot(p, snapshotFileContents))
        .andThen(ignored -> transientSnapshot.persist(), control);
  }

  public static void writeSnapshot(
      final Path path, final Map<String, String> snapshotFileContents) {
    try {
      FileUtil.ensureDirectoryExists(path);

      for (final var entry : snapshotFileContents.entrySet()) {
        final var fileName = path.resolve(entry.getKey());
        final var fileContent = entry.getValue().getBytes(StandardCharsets.UTF_8);
        Files.write(fileName, fileContent, CREATE_NEW, StandardOpenOption.WRITE);
      }
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
