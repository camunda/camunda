/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.nodeid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class VersionedNodeIdBasedDataDirectoryProviderTest {

  private static final String DIRECTORY_INITIALIZED_FILE = "directory-initialized.json";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final int DEFAULT_RETENTION_COUNT = 2;

  @TempDir Path tempDir;

  @Test
  void shouldCreateEmptyDirectoryWhenNoPreviousVersionExists() throws Exception {
    // given
    final var nodeId = 5;
    final var nodeVersion = 3L;

    final var nodeInstance = new NodeInstance(nodeId, Version.of(nodeVersion));

    final var rootDirectory = tempDir.resolve("root");
    final var copier = new RecordingDataDirectoryCopier();
    final DataDirectoryProvider initializer =
        new VersionedNodeIdBasedDataDirectoryProvider(
            OBJECT_MAPPER, nodeInstance, copier, false, DEFAULT_RETENTION_COUNT);

    // when
    final var result = initializer.initialize(rootDirectory);

    // then
    assertThat(result).isCompleted();

    final var directory = rootDirectory.resolve("node-5").resolve("v3");
    assertThat(result.join()).isEqualTo(directory);
    assertThat(directory).exists().isDirectory();

    assertThat(copier.invocations()).isEmpty();

    final var initInfo = readInitializationInfo(directory);
    assertThat(initInfo.initializedAt()).isGreaterThan(0L);
    assertThat(initInfo.version().version()).isEqualTo(3L);
    assertThat(initInfo.initializedFrom()).isNull();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldCopyFromPreviousInitializedVersion(final boolean gracefulShutdown) throws Exception {
    // given
    final var nodeId = 2;
    final var nodeInstance = new NodeInstance(nodeId, Version.of(4L));

    final var rootDirectory = tempDir.resolve("root");

    final var previous = rootDirectory.resolve("node-2").resolve("v3");
    final var previousPartition =
        previous.resolve("raft-partition").resolve("partitions").resolve("1");

    Files.createDirectories(previousPartition);
    Files.writeString(previousPartition.resolve("atomix-partition-1.meta"), "meta");
    Files.writeString(previousPartition.resolve("atomix-partition-1.conf"), "conf");
    Files.writeString(previousPartition.resolve("atomix-partition-1-1.log"), "log");

    writeInitializationFile(previous, 3L, null);

    final var copier = new RecordingDataDirectoryCopier();
    final DataDirectoryProvider initializer =
        new VersionedNodeIdBasedDataDirectoryProvider(
            OBJECT_MAPPER, nodeInstance, copier, gracefulShutdown, DEFAULT_RETENTION_COUNT);

    // when
    final var newDirectory = initializer.initialize(rootDirectory).join();

    // then
    assertThat(newDirectory).isEqualTo(rootDirectory.resolve("node-2").resolve("v4"));

    final var copiedPartition =
        newDirectory.resolve("raft-partition").resolve("partitions").resolve("1");

    assertThat(copiedPartition.resolve("atomix-partition-1.meta")).exists();
    assertThat(copiedPartition.resolve("atomix-partition-1.conf")).exists();
    assertThat(copiedPartition.resolve("atomix-partition-1-1.log")).exists();

    assertThat(copier.invocations()).hasSize(1);
    assertThat(copier.invocations().getFirst().source()).isEqualTo(previous);
    assertThat(copier.invocations().getFirst().target())
        .isEqualTo(rootDirectory.resolve("node-2").resolve("v4"));
    assertThat(copier.invocations().getFirst().markerFileName())
        .isEqualTo(DIRECTORY_INITIALIZED_FILE);
    assertThat(copier.useHardLinks).isEqualTo(gracefulShutdown);

    final var initInfo = readInitializationInfo(newDirectory);
    assertThat(initInfo.initializedAt()).isGreaterThan(0L);
    assertThat(initInfo.version().version()).isEqualTo(4L);
    assertThat(initInfo.initializedFrom().version()).isEqualTo(3L);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldCopyFromLatestValidPreviousVersion(final boolean gracefulShutdown) throws Exception {
    // given
    final var nodeId = 1;
    final var nodeInstance = new NodeInstance(nodeId, Version.of(3L));

    final var rootDirectory = tempDir.resolve("root");
    final var nodeDirectory = rootDirectory.resolve("node-1");

    createValidVersionDirectory(nodeDirectory, 0L, "file0.txt", "content0");
    createValidVersionDirectory(nodeDirectory, 2L, "file2.txt", "content2");

    final var copier = new RecordingDataDirectoryCopier();
    final DataDirectoryProvider initializer =
        new VersionedNodeIdBasedDataDirectoryProvider(
            OBJECT_MAPPER, nodeInstance, copier, gracefulShutdown, DEFAULT_RETENTION_COUNT);

    // when
    final var result = initializer.initialize(rootDirectory).join();

    // then
    assertThat(result).isEqualTo(nodeDirectory.resolve("v3"));
    assertThat(result.resolve("file2.txt")).exists();
    assertThat(Files.readString(result.resolve("file2.txt"))).isEqualTo("content2");

    assertThat(copier.invocations()).hasSize(1);
    assertThat(copier.invocations().getFirst().source()).isEqualTo(nodeDirectory.resolve("v2"));
    assertThat(copier.invocations().getFirst().target()).isEqualTo(nodeDirectory.resolve("v3"));
    assertThat(copier.useHardLinks).isEqualTo(gracefulShutdown);

    final var initInfo = readInitializationInfo(result);
    assertThat(initInfo.version().version()).isEqualTo(3L);
    assertThat(initInfo.initializedFrom().version()).isEqualTo(2L);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldHandleVersionZeroAsPrevious(final boolean gracefulShutdown) throws Exception {
    // given
    final var nodeId = 1;
    final var nodeInstance = new NodeInstance(nodeId, Version.of(1L));

    final var rootDirectory = tempDir.resolve("root");
    final var nodeDirectory = rootDirectory.resolve("node-1");

    createValidVersionDirectory(nodeDirectory, 0L, "file0.txt", "version 0 content");

    final var copier = new RecordingDataDirectoryCopier();
    final DataDirectoryProvider initializer =
        new VersionedNodeIdBasedDataDirectoryProvider(
            OBJECT_MAPPER, nodeInstance, copier, gracefulShutdown, DEFAULT_RETENTION_COUNT);

    // when
    final var result = initializer.initialize(rootDirectory).join();

    // then
    assertThat(result).isEqualTo(nodeDirectory.resolve("v1"));
    assertThat(result.resolve("file0.txt")).exists();
    assertThat(Files.readString(result.resolve("file0.txt"))).isEqualTo("version 0 content");

    assertThat(copier.useHardLinks).isEqualTo(gracefulShutdown);

    final var initInfo = readInitializationInfo(result);
    assertThat(initInfo.version().version()).isEqualTo(1L);
    assertThat(initInfo.initializedFrom().version()).isEqualTo(0L);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldSkipInvalidPreviousVersionsAndUseLatestValid(final boolean gracefulShutdown)
      throws Exception {
    // given
    final var nodeId = 1;
    final var nodeInstance = new NodeInstance(nodeId, Version.of(4L));

    final var rootDirectory = tempDir.resolve("root");
    final var nodeDirectory = rootDirectory.resolve("node-1");

    createValidVersionDirectory(nodeDirectory, 1L, "file1.txt", "content1");

    // invalid version 2: directory exists but no initialization file
    final var invalidVersion2 = nodeDirectory.resolve("v2");
    Files.createDirectories(invalidVersion2);
    Files.writeString(invalidVersion2.resolve("file2.txt"), "content2");

    // invalid version 3: directory exists but no initialization file
    final var invalidVersion3 = nodeDirectory.resolve("v3");
    Files.createDirectories(invalidVersion3);
    Files.writeString(invalidVersion3.resolve("file3.txt"), "content3");

    final var copier = new RecordingDataDirectoryCopier();
    final DataDirectoryProvider initializer =
        new VersionedNodeIdBasedDataDirectoryProvider(
            OBJECT_MAPPER, nodeInstance, copier, gracefulShutdown, DEFAULT_RETENTION_COUNT);

    // when
    final var result = initializer.initialize(rootDirectory).join();

    // then
    assertThat(result).isEqualTo(nodeDirectory.resolve("v4"));

    assertThat(result.resolve("file1.txt")).exists();
    assertThat(Files.readString(result.resolve("file1.txt"))).isEqualTo("content1");
    assertThat(result.resolve("file2.txt")).doesNotExist();
    assertThat(result.resolve("file3.txt")).doesNotExist();

    assertThat(copier.invocations()).hasSize(1);
    assertThat(copier.invocations().getFirst().source()).isEqualTo(nodeDirectory.resolve("v1"));
    assertThat(copier.invocations().getFirst().target()).isEqualTo(nodeDirectory.resolve("v4"));
    assertThat(copier.useHardLinks).isEqualTo(gracefulShutdown);

    final var initInfo = readInitializationInfo(result);
    assertThat(initInfo.version().version()).isEqualTo(4L);
    assertThat(initInfo.initializedFrom().version()).isEqualTo(1L);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldDeleteAndReinitializeWhenTargetExistsButNotInitialized(final boolean gracefulShutdown)
      throws Exception {
    // given
    final var nodeId = 1;
    final var nodeInstance = new NodeInstance(nodeId, Version.of(2L));

    final var rootDirectory = tempDir.resolve("root");

    final var nodeDirectory = rootDirectory.resolve("node-1");
    createValidVersionDirectory(nodeDirectory, 1L, "file1.txt", "content1");

    // create target directory with garbage but without init marker
    final var targetDirectory = nodeDirectory.resolve("v2");
    Files.createDirectories(targetDirectory);
    Files.writeString(targetDirectory.resolve("garbage.txt"), "garbage");

    final var copier = new RecordingDataDirectoryCopier();
    final DataDirectoryProvider initializer =
        new VersionedNodeIdBasedDataDirectoryProvider(
            OBJECT_MAPPER, nodeInstance, copier, gracefulShutdown, DEFAULT_RETENTION_COUNT);

    // when
    final var result = initializer.initialize(rootDirectory).join();

    // then
    assertThat(result).isEqualTo(targetDirectory);
    assertThat(result.resolve("garbage.txt")).doesNotExist();
    assertThat(result.resolve("file1.txt")).exists();

    assertThat(copier.useHardLinks).isEqualTo(gracefulShutdown);

    final var initInfo = readInitializationInfo(result);
    assertThat(initInfo.version().version()).isEqualTo(2L);
    assertThat(initInfo.initializedFrom().version()).isEqualTo(1L);
  }

  @Test
  void shouldGarbageCollectOldVersionsAfterInitialization() throws Exception {
    // given
    final var nodeId = 1;
    final var nodeInstance = new NodeInstance(nodeId, Version.of(5L));

    final var rootDirectory = tempDir.resolve("root");
    final var nodeDirectory = rootDirectory.resolve("node-1");

    // Create 4 previous versions (v1, v2, v3, v4)
    createValidVersionDirectory(nodeDirectory, 1L, "file1.txt", "content1");
    createValidVersionDirectory(nodeDirectory, 2L, "file2.txt", "content2");
    createValidVersionDirectory(nodeDirectory, 3L, "file3.txt", "content3");
    createValidVersionDirectory(nodeDirectory, 4L, "file4.txt", "content4");

    final var copier = new RecordingDataDirectoryCopier();
    // retention count of 2 means keep only v4 and v5 (the new one)
    final int retentionCount = 2;
    final DataDirectoryProvider initializer =
        new VersionedNodeIdBasedDataDirectoryProvider(
            OBJECT_MAPPER, nodeInstance, copier, false, retentionCount);

    // when
    final var result = initializer.initialize(rootDirectory).join();

    // then
    assertThat(result).isEqualTo(nodeDirectory.resolve("v5"));

    // v5 (new) and v4 (source) should exist, older versions should be garbage collected
    assertThat(nodeDirectory.resolve("v5")).exists();
    assertThat(nodeDirectory.resolve("v4")).exists();
    assertThat(nodeDirectory.resolve("v3")).doesNotExist();
    assertThat(nodeDirectory.resolve("v2")).doesNotExist();
    assertThat(nodeDirectory.resolve("v1")).doesNotExist();
  }

  @Test
  void shouldNotGarbageCollectWhenNoPreviousVersionExists() throws Exception {
    // given
    final var nodeId = 1;
    final var nodeInstance = new NodeInstance(nodeId, Version.of(1L));

    final var rootDirectory = tempDir.resolve("root");
    final var nodeDirectory = rootDirectory.resolve("node-1");

    final var copier = new RecordingDataDirectoryCopier();
    final DataDirectoryProvider initializer =
        new VersionedNodeIdBasedDataDirectoryProvider(
            OBJECT_MAPPER, nodeInstance, copier, false, DEFAULT_RETENTION_COUNT);

    // when
    final var result = initializer.initialize(rootDirectory).join();

    // then - should succeed without GC running (no previous versions)
    assertThat(result).isEqualTo(nodeDirectory.resolve("v1"));
    assertThat(nodeDirectory.resolve("v1")).exists();
  }

  @Test
  void shouldFailWhenNodeInstanceIsNull() {
    // given
    final var baseDirectory = tempDir.resolve("base");
    final DataDirectoryProvider initializer =
        new VersionedNodeIdBasedDataDirectoryProvider(
            OBJECT_MAPPER,
            null,
            new RecordingDataDirectoryCopier(),
            false,
            DEFAULT_RETENTION_COUNT);

    // when
    final var result = initializer.initialize(baseDirectory);

    // then
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::join)
        .hasCauseInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Node instance is not available");
  }

  private static void createValidVersionDirectory(
      final Path nodeDirectory, final long version, final String fileName, final String content)
      throws IOException {
    final var versionDir = nodeDirectory.resolve("v" + version);
    Files.createDirectories(versionDir);
    Files.writeString(versionDir.resolve(fileName), content);
    writeInitializationFile(versionDir, version, null);
  }

  private static void writeInitializationFile(
      final Path directory, final long currentVersion, final Long initializedFrom)
      throws IOException {
    final var initFile = directory.resolve(DIRECTORY_INITIALIZED_FILE);

    final var initInfo = OBJECT_MAPPER.createObjectNode();
    initInfo.put("initializedAt", System.currentTimeMillis());
    initInfo.put("version", currentVersion);

    if (initializedFrom != null) {
      initInfo.put("initializedFrom", initializedFrom);
    } else {
      initInfo.putNull("initializedFrom");
    }

    OBJECT_MAPPER.writeValue(initFile.toFile(), initInfo);
  }

  private static DirectoryInitializationInfo readInitializationInfo(final Path directory)
      throws IOException {
    final Path initFile = directory.resolve(DIRECTORY_INITIALIZED_FILE);
    return OBJECT_MAPPER.readValue(initFile.toFile(), DirectoryInitializationInfo.class);
  }

  private static final class RecordingDataDirectoryCopier implements DataDirectoryCopier {

    volatile boolean useHardLinks = false;
    private final List<Invocation> invocations = new ArrayList<>();

    List<Invocation> invocations() {
      return invocations;
    }

    @Override
    public void copy(
        final Path source,
        final Path target,
        final String markerFileName,
        final boolean useHardLinks)
        throws IOException {
      this.useHardLinks = useHardLinks;
      invocations.add(new Invocation(source, target, markerFileName));

      Files.walkFileTree(
          source,
          new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(
                final Path dir, final BasicFileAttributes attrs) throws IOException {
              Files.createDirectories(target.resolve(source.relativize(dir)));
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs)
                throws IOException {
              final var relative = source.relativize(file);
              if (relative.getFileName().toString().equals(markerFileName)) {
                return FileVisitResult.CONTINUE;
              }

              final var targetFile = target.resolve(relative);
              Files.createDirectories(targetFile.getParent());
              Files.copy(file, targetFile, StandardCopyOption.COPY_ATTRIBUTES);
              return FileVisitResult.CONTINUE;
            }
          });
    }

    @Override
    public void validate(final Path source, final Path target, final String markerFileName)
        throws IOException {
      // no-op for recording copier
    }

    private record Invocation(Path source, Path target, String markerFileName) {}
  }
}
