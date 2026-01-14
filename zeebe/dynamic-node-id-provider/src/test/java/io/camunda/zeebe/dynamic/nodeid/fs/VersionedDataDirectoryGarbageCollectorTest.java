/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.nodeid.fs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.dynamic.nodeid.Version;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class VersionedDataDirectoryGarbageCollectorTest {

  private static final String MARKER_FILE = "marker.txt";

  @TempDir Path tempDir;

  private VersionedDirectoryLayout layout;

  @BeforeEach
  void setUp() {
    layout = new VersionedDirectoryLayout(tempDir.resolve("node-1"), ObjectMapperInstance.INSTANCE);
  }

  @ParameterizedTest
  @ValueSource(ints = {2, 3, 10})
  void shouldNotDeleteAnythingWhenValidVersionsAtOrBelowRetentionCount(final int retentionCount)
      throws IOException {
    // given - 2 valid versions
    createInitializedVersionDirectory(1);
    createInitializedVersionDirectory(2);

    final var gc = new VersionedDataDirectoryGarbageCollector(layout, retentionCount);

    // when
    final var deleted = gc.collect();

    // then
    assertThat(deleted).isEmpty();
    assertVersionDirectoryExists(1);
    assertVersionDirectoryExists(2);
  }

  @ParameterizedTest
  @MethodSource("provideRetentionCountAndExpectedDeletions")
  void shouldDeleteOldestValidVersionsWhenMoreThanRetentionCount(
      final int retentionCount, final long[] expectedDeleted, final long[] expectedKept)
      throws IOException {
    // given - 4 valid versions
    createInitializedVersionDirectory(1);
    createInitializedVersionDirectory(2);
    createInitializedVersionDirectory(3);
    createInitializedVersionDirectory(4);

    final var gc = new VersionedDataDirectoryGarbageCollector(layout, retentionCount);

    // when
    final var deleted = gc.collect();

    // then
    assertThat(deleted)
        .containsExactlyInAnyOrder(
            Arrays.stream(expectedDeleted)
                .mapToObj(layout::resolveVersionDirectory)
                .toArray(Path[]::new));
    for (final long version : expectedDeleted) {
      assertVersionDirectoryIsDeleted(version);
    }
    for (final long version : expectedKept) {
      assertVersionDirectoryExists(version);
    }
  }

  private static Stream<Arguments> provideRetentionCountAndExpectedDeletions() {
    return Stream.of(
        Arguments.of(1, new long[] {1, 2, 3}, new long[] {4}),
        Arguments.of(2, new long[] {1, 2}, new long[] {3, 4}),
        Arguments.of(3, new long[] {1}, new long[] {2, 3, 4}));
  }

  @Test
  void shouldHandleNonConsecutiveVersionNumbers() throws IOException {
    // given
    createInitializedVersionDirectory(1);
    createInitializedVersionDirectory(5);
    createInitializedVersionDirectory(10);
    createInitializedVersionDirectory(100);

    final var gc = new VersionedDataDirectoryGarbageCollector(layout, 2);

    // when
    final var deleted = gc.collect();

    // then
    assertThat(deleted)
        .containsExactlyInAnyOrder(
            layout.resolveVersionDirectory(1), layout.resolveVersionDirectory(5));
    assertVersionDirectoryIsDeleted(1);
    assertVersionDirectoryIsDeleted(5);
    assertVersionDirectoryExists(10);
    assertVersionDirectoryExists(100);
  }

  @Test
  void shouldIgnoreNonVersionDirectories() throws IOException {
    // given
    final var nodeDirectory = layout.nodeDirectory();
    createInitializedVersionDirectory(1);
    createInitializedVersionDirectory(2);
    createInitializedVersionDirectory(3);

    // non-version directories that should be ignored
    Files.createDirectories(nodeDirectory.resolve("other"));
    Files.createDirectories(nodeDirectory.resolve("version1")); // wrong prefix
    Files.writeString(nodeDirectory.resolve("v4.txt"), "file"); // file, not directory

    final var gc = new VersionedDataDirectoryGarbageCollector(layout, 2);

    // when
    final var deleted = gc.collect();

    // then
    assertThat(deleted).containsExactly(layout.resolveVersionDirectory(1));
    assertVersionDirectoryIsDeleted(1);
    assertVersionDirectoryExists(2);
    assertVersionDirectoryExists(3);
    assertThat(nodeDirectory.resolve("other")).exists().isDirectory();
    assertThat(nodeDirectory.resolve("version1")).exists().isDirectory();
    assertThat(nodeDirectory.resolve("v4.txt")).exists().isRegularFile();
  }

  @Test
  void shouldHandleVersionZero() throws IOException {
    // given
    createInitializedVersionDirectory(0);
    createInitializedVersionDirectory(1);
    createInitializedVersionDirectory(2);

    final var gc = new VersionedDataDirectoryGarbageCollector(layout, 2);

    // when
    final var deleted = gc.collect();

    // then
    assertThat(deleted).containsExactly(layout.resolveVersionDirectory(0));
    assertVersionDirectoryIsDeleted(0);
    assertVersionDirectoryExists(1);
    assertVersionDirectoryExists(2);
  }

  @Test
  void shouldThrowExceptionWhenNodeDirectoryDoesNotExist() {
    // given
    final var nonExistentLayout =
        new VersionedDirectoryLayout(
            tempDir.resolve("non-existent"), ObjectMapperInstance.INSTANCE);
    final var gc = new VersionedDataDirectoryGarbageCollector(nonExistentLayout, 2);

    // when/then
    assertThatThrownBy(gc::collect)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Node directory does not exist");
  }

  @Test
  void shouldReturnEmptyListWhenNodeDirectoryIsEmpty() throws IOException {
    // given - only create the directory, no versions
    Files.createDirectories(layout.nodeDirectory());

    final var gc = new VersionedDataDirectoryGarbageCollector(layout, 2);

    // when
    final var deleted = gc.collect();

    // then
    assertThat(deleted).isEmpty();
  }

  @Test
  void shouldDeleteDirectoryWithContents() throws IOException {
    // given
    final var v1 = createInitializedVersionDirectory(1);
    Files.writeString(v1.resolve("file1.txt"), "content1");
    Files.createDirectories(v1.resolve("subdir"));
    Files.writeString(v1.resolve("subdir").resolve("file2.txt"), "content2");

    createInitializedVersionDirectory(2);
    createInitializedVersionDirectory(3);

    final var gc = new VersionedDataDirectoryGarbageCollector(layout, 2);

    // when
    final var deleted = gc.collect();

    // then
    assertThat(deleted).containsExactly(layout.resolveVersionDirectory(1));
    assertVersionDirectoryIsDeleted(1);
    assertVersionDirectoryExists(2);
    assertVersionDirectoryExists(3);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1, -10})
  void shouldThrowExceptionWhenRetentionCountIsInvalid(final int retentionCount) {
    assertThatThrownBy(() -> new VersionedDataDirectoryGarbageCollector(layout, retentionCount))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("retentionCount must be at least 1");
  }

  @Test
  void shouldAlwaysDeleteInvalidDirectories() throws IOException {
    // given - v2 and v3 are invalid, v1 and v4 are valid
    createInitializedVersionDirectory(1);
    createVersionDirectory(2);
    createVersionDirectory(3);
    createInitializedVersionDirectory(4);

    final var gc = new VersionedDataDirectoryGarbageCollector(layout, 2);

    // when
    final var deleted = gc.collect();

    // then - invalid directories are always deleted, valid ones are kept
    assertThat(deleted)
        .containsExactlyInAnyOrder(
            layout.resolveVersionDirectory(2), layout.resolveVersionDirectory(3));
    assertVersionDirectoryExists(1);
    assertVersionDirectoryIsDeleted(2);
    assertVersionDirectoryIsDeleted(3);
    assertVersionDirectoryExists(4);
  }

  @Test
  void shouldKeepAtLeastRetentionCountValidDirectoriesEvenIfOlderThanInvalid() throws IOException {
    // given - v1 valid, v2 invalid, v3 valid, v4 valid (newest)
    // With retention=2, we should keep v4 and v3 (newest valid), delete v2 (invalid), delete v1
    // (valid but exceeds retention)
    createInitializedVersionDirectory(1);
    createVersionDirectory(2);
    createInitializedVersionDirectory(3);
    createInitializedVersionDirectory(4);

    final var gc = new VersionedDataDirectoryGarbageCollector(layout, 2);

    // when
    final var deleted = gc.collect();

    // then
    assertThat(deleted)
        .containsExactlyInAnyOrder(
            layout.resolveVersionDirectory(1), layout.resolveVersionDirectory(2));
    assertVersionDirectoryIsDeleted(1); // valid but exceeds retention
    assertVersionDirectoryIsDeleted(2); // invalid, always deleted
    assertVersionDirectoryExists(3); // valid, kept
    assertVersionDirectoryExists(4); // valid, kept (newest)
  }

  @Test
  void shouldNotDeleteValidDirectoriesWhenOnlyInvalidExceedsRetention() throws IOException {
    // given - v1 valid, v2 valid, v3 invalid (newest)
    // With retention=2, we have 2 valid directories, so we should only delete the invalid one
    createInitializedVersionDirectory(1);
    createInitializedVersionDirectory(2);
    createVersionDirectory(3);

    final var gc = new VersionedDataDirectoryGarbageCollector(layout, 2);

    // when
    final var deleted = gc.collect();

    // then - only the invalid directory is deleted
    assertThat(deleted).containsExactly(layout.resolveVersionDirectory(3));
    assertVersionDirectoryExists(1);
    assertVersionDirectoryExists(2);
    assertVersionDirectoryIsDeleted(3);
  }

  @Test
  void shouldDeleteAllInvalidDirectoriesEvenWhenBelowRetentionCount() throws IOException {
    // given - v1 invalid, v2 valid (only 1 valid, below retention of 2)
    createVersionDirectory(1);
    createInitializedVersionDirectory(2);

    final var gc = new VersionedDataDirectoryGarbageCollector(layout, 2);

    // when
    final var deleted = gc.collect();

    // then - invalid directory is deleted even though we only have 1 valid directory
    assertThat(deleted).containsExactly(layout.resolveVersionDirectory(1));
    assertVersionDirectoryIsDeleted(1);
    assertVersionDirectoryExists(2);
  }

  private Path createInitializedVersionDirectory(final long version) throws IOException {
    final var versionDir = createVersionDirectory(version);
    layout.initializeDirectory(Version.of(version), null);
    return versionDir;
  }

  private Path createVersionDirectory(final long version) throws IOException {
    final var versionDir = layout.resolveVersionDirectory(version);
    Files.createDirectories(versionDir);
    return versionDir;
  }

  private void assertVersionDirectoryExists(final long version) {
    final var versionDir = layout.resolveVersionDirectory(version);
    assertThat(versionDir).as("Version directory v%d should exist", version).exists().isDirectory();
    assertThat(layout.isDirectoryInitialized(Version.of(version))).isTrue();
  }

  private void assertVersionDirectoryIsDeleted(final long version) {
    final var versionDir = layout.resolveVersionDirectory(version);
    assertThat(versionDir).as("Version directory v%d should be deleted", version).doesNotExist();
  }
}
