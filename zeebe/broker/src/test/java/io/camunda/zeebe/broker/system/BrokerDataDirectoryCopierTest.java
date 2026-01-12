/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStoreImpl;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class BrokerDataDirectoryCopierTest {

  private static final String MARKER_FILE = "directory-initialized.json";

  @TempDir Path tempDir;

  @Test
  void shouldSkipRuntimeDirectoryAndMarkerFile() throws Exception {
    // given
    final var source = tempDir.resolve("source");
    final var target = tempDir.resolve("target");

    Files.createDirectories(source.resolve("partitions/1"));
    Files.writeString(source.resolve("partitions/1/file.log"), "content");

    Files.writeString(source.resolve(MARKER_FILE), "ignored");

    Files.createDirectories(source.resolve("runtime/inner"));
    Files.writeString(source.resolve("runtime/inner/ignored.txt"), "ignored");

    final var copier = new BrokerDataDirectoryCopier();

    // when
    copier.copy(source, target, MARKER_FILE, false);

    // then
    assertThat(target.resolve("partitions/1/file.log")).exists();
    assertThat(target.resolve(MARKER_FILE)).doesNotExist();
    assertThat(target.resolve("runtime")).doesNotExist();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void shouldHardLinkSnapshotFilesWhenPossible(final boolean gracefulShutdown) throws Exception {
    // given
    final var source = tempDir.resolve("source");
    final var target = tempDir.resolve("target");

    final var snapshotFile =
        source
            .resolve("partitions/1")
            .resolve(FileBasedSnapshotStoreImpl.SNAPSHOTS_DIRECTORY)
            .resolve("snap-1")
            .resolve("file.bin");
    Files.createDirectories(snapshotFile.getParent());
    Files.writeString(snapshotFile, "abc");

    final var bootstrapSnapshotFile =
        source
            .resolve("partitions/1")
            .resolve(FileBasedSnapshotStoreImpl.SNAPSHOTS_BOOTSTRAP_DIRECTORY)
            .resolve("snap-0")
            .resolve("file.bin");
    Files.createDirectories(bootstrapSnapshotFile.getParent());
    Files.writeString(bootstrapSnapshotFile, "def");

    // Create regular non-snapshot files for graceful shutdown test
    final var regularFile = source.resolve("partitions/1/file.log");
    Files.createDirectories(regularFile.getParent());
    Files.writeString(regularFile, "log content");

    final var anotherFile = source.resolve("partitions/2/segments.dat");
    Files.createDirectories(anotherFile.getParent());
    Files.writeString(anotherFile, "segment content");

    final var copier = new BrokerDataDirectoryCopier();

    // when
    copier.copy(source, target, MARKER_FILE, gracefulShutdown);

    // then - snapshot files should always be hard-linked (or copied with same content)
    assertHardLinkedOrCopiedWithSameContent(
        snapshotFile,
        target
            .resolve("partitions/1")
            .resolve(FileBasedSnapshotStoreImpl.SNAPSHOTS_DIRECTORY)
            .resolve("snap-1")
            .resolve("file.bin"));

    assertHardLinkedOrCopiedWithSameContent(
        bootstrapSnapshotFile,
        target
            .resolve("partitions/1")
            .resolve(FileBasedSnapshotStoreImpl.SNAPSHOTS_BOOTSTRAP_DIRECTORY)
            .resolve("snap-0")
            .resolve("file.bin"));

    // Regular files should only be hard-linked when gracefulShutdown is true
    assertHardLinkedIfGraceful(
        regularFile, target.resolve("partitions/1/file.log"), gracefulShutdown);
    assertHardLinkedIfGraceful(
        anotherFile, target.resolve("partitions/2/segments.dat"), gracefulShutdown);
  }

  private static void assertHardLinked(final Path source, final Path target) throws IOException {
    assertThat(source).exists();
    assertThat(target).exists();

    // Both should point to the same inode/file key
    assertThat(areHardLinked(source, target)).isTrue();
  }

  private static void assertHardLinkedIfGraceful(
      final Path source, final Path target, final boolean gracefulShutdown) throws IOException {
    assertThat(target).exists();

    if (gracefulShutdown) {
      // Should be hard-linked
      assertHardLinked(source, target);
    } else {
      // Should exist but not necessarily hard-linked (could be a copy)
      assertThat(source).exists();
    }
  }

  private static void assertHardLinkedOrCopiedWithSameContent(final Path source, final Path target)
      throws IOException {
    assertThat(target).exists();

    // if hard-linking succeeded, both point to the same inode/file key
    if (areHardLinked(source, target)) {
      // Files are hard-linked, assertion passes
      return;
    }

    // Otherwise, verify content is the same
    assertThat(Files.readAllBytes(target)).isEqualTo(Files.readAllBytes(source));
  }

  @Test
  void shouldCopyDirectoryStructureRecursively() throws Exception {
    // given
    final var source = tempDir.resolve("source");
    final var target = tempDir.resolve("target");

    Files.createDirectories(source.resolve("subdir1/subdir2"));
    Files.writeString(source.resolve("root-file.txt"), "root content");
    Files.writeString(source.resolve("subdir1/file1.txt"), "file1 content");
    Files.writeString(source.resolve("subdir1/subdir2/file2.txt"), "file2 content");

    final var copier = new BrokerDataDirectoryCopier();

    // when
    copier.copy(source, target, MARKER_FILE, false);

    // then
    assertThat(target.resolve("root-file.txt")).exists();
    assertThat(target.resolve("subdir1/file1.txt")).exists();
    assertThat(target.resolve("subdir1/subdir2/file2.txt")).exists();

    assertThat(Files.readString(target.resolve("root-file.txt"))).isEqualTo("root content");
    assertThat(Files.readString(target.resolve("subdir1/file1.txt"))).isEqualTo("file1 content");
    assertThat(Files.readString(target.resolve("subdir1/subdir2/file2.txt")))
        .isEqualTo("file2 content");
  }

  private static boolean areHardLinked(final Path source, final Path target) throws IOException {
    final var sourceKey = Files.readAttributes(source, BasicFileAttributes.class).fileKey();
    final var targetKey = Files.readAttributes(target, BasicFileAttributes.class).fileKey();

    return sourceKey != null && targetKey != null && sourceKey.equals(targetKey);
  }
}
