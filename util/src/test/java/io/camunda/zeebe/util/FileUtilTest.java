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
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import jnr.posix.FileStat;
import jnr.posix.POSIX;
import jnr.posix.POSIXFactory;
import jnr.posix.util.Platform;
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

  @Test
  void shouldPreallocateFile() throws IOException {
    // given
    final var path = tmpDir.resolve("file");
    final var length = 1024 * 1024L;

    // when
    try (final FileChannel channel =
        FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
      FileUtil.preallocate(channel, length);
    }

    // then
    final var realSize = getRealSize(path);
    final var maxRealSize = length + getBlockSize(path);
    assertThat(realSize)
        .as(
            "Expected <%s> to have a real size between <%d> and <%d> bytes, but it had <%d>",
            path, length, maxRealSize, realSize)
        .isBetween(length, maxRealSize);
  }

  /**
   * Returns the actual size of the file on disk by checking the blocks allocated for this file. On
   * most modern UNIX systems, doing {@link Files#size(Path)} returns that size as reported by the
   * file's metadata, which may not be the real size (e.g. compressed file systems, sparse files,
   * etc.). Using the {@code lstat} function from the C library we can get the actual size on disk
   * of the file.
   *
   * <p>{@code lstat} will return the number of 512-bytes blocks used by a file. To get the real
   * size, you simply multiply by 512. Note that unless your file size is aligned with the block
   * size of your device, then the real size may be slightly larger, as more blocks may have been
   * allocated.
   *
   * <p>NOTE: on Windows, sparse files are not the default, so {@link File#length()} is appropriate.
   * Plus, there is no {@code lstat} function, and the equivalent function {@code wstat} does not
   * return the number of blocks.
   *
   * @param file the file to get the size of
   * @return the actual size on disk of the file
   */
  private long getRealSize(final Path file) {
    if (Platform.IS_WINDOWS) {
      try {
        return Files.size(file);
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    final POSIX posixFunctions = POSIXFactory.getNativePOSIX();
    final var pathString = file.toString();
    final FileStat stat = posixFunctions.stat(pathString);

    return stat.blocks() * 512;
  }

  /**
   * Returns the I/O block size of the device containing the given file. This can be used to compute
   * an upper bound for the real file size. On Windows, as we use {@link Files#size(Path)} for the
   * real size, this simply returns 0.
   *
   * @param file the file to get the block size of
   * @return the I/O block size of the device containing the file
   */
  private long getBlockSize(final Path file) {
    if (Platform.IS_WINDOWS) {
      return 0;
    }

    final POSIX posixFunctions = POSIXFactory.getNativePOSIX();
    final var pathString = file.toString();
    final FileStat stat = posixFunctions.stat(pathString);

    return stat.blockSize();
  }
}
