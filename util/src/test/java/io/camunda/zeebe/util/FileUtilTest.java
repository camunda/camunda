/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class FileUtilTest {
  private @TempDir Path tmpDir;

  @Test
  void shouldDeleteFolder() throws IOException {
    Files.createFile(tmpDir.resolve("file1"));
    Files.createFile(tmpDir.resolve("file2"));
    Files.createDirectory(tmpDir.resolve("testFolder"));

    FileUtil.deleteFolder(tmpDir);

    assertThat(tmpDir).doesNotExist();
  }

  @Test
  void shouldThrowExceptionForNonExistingFolder() throws IOException {
    final Path root = Files.createDirectory(tmpDir.resolve("other"));
    Files.delete(root);

    assertThatThrownBy(() -> FileUtil.deleteFolder(root)).isInstanceOf(NoSuchFileException.class);
  }

  // regression test
  @Test
  void shouldNotThrowErrorIfFolderDoesNotExist() {
    // given
    final Path nonExistent = tmpDir.resolve("something");

    // when - then
    assertThatCode(() -> FileUtil.deleteFolderIfExists(nonExistent))
        .as("no error if folder does not exist")
        .doesNotThrowAnyException();
  }

  @Test
  void shouldThrowExceptionWhenCopySnapshotForNonExistingFolder() {
    // given
    final File source = tmpDir.resolve("src").toFile();
    final File target = tmpDir.resolve("target").toFile();

    // when - then
    assertThatThrownBy(() -> FileUtil.copySnapshot(source.toPath(), target.toPath()))
        .isInstanceOf(NoSuchFileException.class);
  }

  @Test
  void shouldThrowExceptionWhenyCopySnapshotIfTargetAlreadyExists() throws IOException {
    // given
    final Path source = Files.createDirectory(tmpDir.resolve("src"));
    final Path target = Files.createDirectory(tmpDir.resolve("target"));

    // when -then
    assertThatThrownBy(() -> FileUtil.copySnapshot(source, target))
        .isInstanceOf(FileAlreadyExistsException.class);
  }

  @Test
  void shouldCopySnapshot() throws Exception {
    // given
    final var snapshotFile = "file1";
    final Path source = Files.createDirectory(tmpDir.resolve("src"));
    final Path target = tmpDir.resolve("target");
    Files.createFile(source.resolve(snapshotFile));

    // when
    FileUtil.copySnapshot(source, target);

    // then
    assertThat(Files.list(target)).contains(target.resolve(snapshotFile));
  }
}
