/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.snapshots.impl;

import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.util.FileUtil;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class FileBasedSnapshotTest {
  private static final Map<String, String> SNAPSHOT_FILE_CONTENTS =
      Map.of(
          "file1", "file1 contents",
          "file2", "file2 contents");

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private Path snapshotDir;

  @Before
  public void beforeEach() throws Exception {
    snapshotDir = temporaryFolder.newFolder("store", "snapshots").toPath();
  }

  @Test
  public void shouldDeleteSnapshot() throws IOException {
    // given
    final var snapshotPath = snapshotDir.resolve("snapshot");
    final Path checksumPath = snapshotDir.resolve("checksum");
    final var snapshot = createSnapshot(snapshotPath, checksumPath);

    // when
    snapshot.delete();

    // then
    assertThat(snapshotPath).doesNotExist();
    assertThat(checksumPath).doesNotExist();
  }

  private FileBasedSnapshot createSnapshot(final Path snapshotPath, final Path checksumPath)
      throws IOException {
    final var metadata = new FileBasedSnapshotMetadata(1L, 1L, 1L, 1L);

    FileUtil.ensureDirectoryExists(snapshotPath);
    for (final var entry : SNAPSHOT_FILE_CONTENTS.entrySet()) {
      final var fileName = snapshotPath.resolve(entry.getKey());
      final var fileContent = entry.getValue().getBytes(StandardCharsets.UTF_8);
      Files.write(fileName, fileContent, CREATE_NEW, StandardOpenOption.WRITE);
    }
    SnapshotChecksum.persist(checksumPath, SnapshotChecksum.calculate(snapshotPath));

    return new FileBasedSnapshot(snapshotPath, checksumPath, 1L, metadata);
  }
}
