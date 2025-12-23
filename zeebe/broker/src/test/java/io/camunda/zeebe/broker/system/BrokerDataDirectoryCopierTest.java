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

final class BrokerDataDirectoryCopierTest {

  private static final String MARKER_FILE = "directory-initialized.json";

  @TempDir Path tempDir;

  @Test
  void shouldSkipRuntimeDirectoryAndMarkerFile() throws Exception {
    // given
    final var source = tempDir.resolve("source");
    final var target = tempDir.resolve("target");

    Files.createDirectories(source.resolve("sub/dir"));
    Files.writeString(source.resolve("sub/dir/file.txt"), "content");

    Files.writeString(source.resolve(MARKER_FILE), "ignored");

    Files.createDirectories(source.resolve("runtime/inner"));
    Files.writeString(source.resolve("runtime/inner/ignored.txt"), "ignored");

    final var copier = new BrokerDataDirectoryCopier();

    // when
    copier.copy(source, target, MARKER_FILE);

    // then
    assertThat(target.resolve("sub/dir/file.txt")).exists();
    assertThat(target.resolve(MARKER_FILE)).doesNotExist();
    assertThat(target.resolve("runtime")).doesNotExist();
  }

  @Test
  void shouldHardLinkSnapshotFilesWhenPossible() throws Exception {
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

    final var copier = new BrokerDataDirectoryCopier();

    // when
    copier.copy(source, target, MARKER_FILE);

    // then
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
  }

  private static void assertHardLinkedOrCopiedWithSameContent(final Path source, final Path target)
      throws IOException {
    assertThat(target).exists();

    final var sourceKey = Files.readAttributes(source, BasicFileAttributes.class).fileKey();
    final var targetKey = Files.readAttributes(target, BasicFileAttributes.class).fileKey();

    // if hard-linking succeeded, both point to the same inode/file key
    if (sourceKey != null && targetKey != null) {
      assertThat(targetKey).isEqualTo(sourceKey);
    } else {
      assertThat(Files.readAllBytes(target)).isEqualTo(Files.readAllBytes(source));
    }
  }
}
