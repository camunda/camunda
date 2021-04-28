/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.snapshots.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.zeebe.util.FileUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class FileBasedSnapshotChunkReaderTest {

  private static final long SNAPSHOT_CHECKSUM = 1L;
  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();
  private Path snapshotDirectory;

  @Test
  public void shouldAssignChunkIdsFromFileNames() throws IOException {
    // given
    final var reader = newReader();

    // when - then
    assertThat(reader.next().getChunkName()).isEqualTo("bar");
    assertThat(reader.next().getChunkName()).isEqualTo("foo");
  }

  @Test
  public void shouldThrowExceptionWhenChunkFileDoesNotExist() throws IOException {
    // given
    final var reader = newReader();

    // when
    Files.delete(snapshotDirectory.resolve("bar"));

    // then
    assertThatThrownBy(reader::next).hasCauseInstanceOf(NoSuchFileException.class);
  }

  @Test
  public void shouldThrowExceptionWhenNoDirectoryExist() throws IOException {
    // given
    final var reader = newReader();

    // when
    FileUtil.deleteFolder(snapshotDirectory);

    // then
    assertThatThrownBy(reader::next).hasCauseInstanceOf(NoSuchFileException.class);
  }

  private FileBasedSnapshotChunkReader newReader() throws IOException {
    snapshotDirectory = temporaryFolder.getRoot().toPath();
    for (final var chunk : Arrays.asList("foo", "bar")) {
      final var path = snapshotDirectory.resolve(chunk);

      Files.createFile(path);
      Files.writeString(path, "content");
    }

    return new FileBasedSnapshotChunkReader(snapshotDirectory, SNAPSHOT_CHECKSUM);
  }
}
