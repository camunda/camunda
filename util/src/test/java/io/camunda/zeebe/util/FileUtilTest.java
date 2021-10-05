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
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class FileUtilTest {

  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  @Test
  public void shouldDeleteFolder() throws IOException {
    final File root = tempFolder.getRoot();

    tempFolder.newFile("file1");
    tempFolder.newFile("file2");
    tempFolder.newFolder("testFolder");

    FileUtil.deleteFolder(root.getAbsolutePath());

    assertThat(root.exists()).isFalse();
  }

  @Test
  public void shouldThrowExceptionForNonExistingFolder() {
    final File root = tempFolder.getRoot();

    tempFolder.delete();

    assertThatThrownBy(
            () -> {
              FileUtil.deleteFolder(root.getAbsolutePath());
            })
        .isInstanceOf(NoSuchFileException.class);
  }

  // regression test
  @Test
  public void shouldNotThrowErrorIfFolderDoesNotExist() {
    // given
    final Path nonExistent = tempFolder.getRoot().toPath().resolve("something");

    // when - then
    assertThatCode(() -> FileUtil.deleteFolderIfExists(nonExistent))
        .as("no error if folder does not exist")
        .doesNotThrowAnyException();
  }

  @Test
  public void shouldThrowExceptionWhenCopySnapshotForNonExistingFolder() {
    // given
    final File source = tempFolder.getRoot().toPath().resolve("src").toFile();
    final File target = tempFolder.getRoot().toPath().resolve("target").toFile();

    // when - then
    assertThatThrownBy(() -> FileUtil.copySnapshot(source.toPath(), target.toPath()))
        .isInstanceOf(NoSuchFileException.class);
  }

  @Test
  public void shouldThrowExceptionWhenyCopySnapshotIfTargetAlreadyExists() throws IOException {
    // given
    final File source = tempFolder.newFolder("src");
    final File target = tempFolder.newFolder("target");

    // when -then
    assertThatThrownBy(() -> FileUtil.copySnapshot(source.toPath(), target.toPath()))
        .isInstanceOf(FileAlreadyExistsException.class);
  }

  @Test
  public void shouldCopySnapshot() throws Exception {
    // given
    final File source = tempFolder.newFolder("src");
    final String snapshotFile = "file1";
    source.toPath().resolve(snapshotFile).toFile().createNewFile();
    final File target = tempFolder.getRoot().toPath().resolve("target").toFile();

    // when
    FileUtil.copySnapshot(source.toPath(), target.toPath());

    // then
    assertThat(target.list()).containsExactly(snapshotFile);
  }
}
