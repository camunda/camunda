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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NodeIdBasedDataDirectoryProviderTest {

  private static final String DIRECTORY_INITIALIZED_FILE = "directory-initialized.json";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
        new NodeIdBasedDataDirectoryProvider(OBJECT_MAPPER, nodeInstance, copier);

    // when
    final var result = initializer.initialize(rootDirectory);

    // then
    assertThat(result).isCompleted();

    final var directory = rootDirectory.resolve("node-5").resolve("v3");
    assertThat(result.join()).isEqualTo(directory);
    assertThat(directory).exists().isDirectory();

    assertThat(copier.invocations()).isEmpty();

    final var initInfo = readInitializationInfo(directory);
    assertThat(initInfo.get("initialized")).isNotNull();
    assertThat(initInfo.get("initializedFrom").isNull()).isTrue();
  }

  @Test
  void shouldCopyFromPreviousInitializedVersion() throws Exception {
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

    writeInitializationFile(previous, null);

    final var copier = new RecordingDataDirectoryCopier();
    final DataDirectoryProvider initializer =
        new NodeIdBasedDataDirectoryProvider(OBJECT_MAPPER, nodeInstance, copier);

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

    final var initInfo = readInitializationInfo(newDirectory);
    assertThat(initInfo.get("initialized")).isNotNull();
    assertThat(initInfo.get("initializedFrom").asLong()).isEqualTo(3L);
  }

  @Test
  void shouldCopyFromLatestValidPreviousVersion() throws Exception {
    // given
    final var nodeId = 1;
    final var nodeInstance = new NodeInstance(nodeId, Version.of(3L));

    final var rootDirectory = tempDir.resolve("root");
    final var nodeDirectory = rootDirectory.resolve("node-1");

    createValidVersionDirectory(nodeDirectory, 0L, "file0.txt", "content0");
    createValidVersionDirectory(nodeDirectory, 2L, "file2.txt", "content2");

    final var copier = new RecordingDataDirectoryCopier();
    final DataDirectoryProvider initializer =
        new NodeIdBasedDataDirectoryProvider(OBJECT_MAPPER, nodeInstance, copier);

    // when
    final var result = initializer.initialize(rootDirectory).join();

    // then
    assertThat(result).isEqualTo(nodeDirectory.resolve("v3"));
    assertThat(result.resolve("file2.txt")).exists();
    assertThat(Files.readString(result.resolve("file2.txt"))).isEqualTo("content2");

    assertThat(copier.invocations()).hasSize(1);
    assertThat(copier.invocations().getFirst().source()).isEqualTo(nodeDirectory.resolve("v2"));
    assertThat(copier.invocations().getFirst().target()).isEqualTo(nodeDirectory.resolve("v3"));

    final var initInfo = readInitializationInfo(result);
    assertThat(initInfo.get("initializedFrom").asLong()).isEqualTo(2L);
  }

  @Test
  void shouldSkipInvalidPreviousVersionsAndUseLatestValid() throws Exception {
    // given
    final var nodeId = 1;
    final var nodeInstance = new NodeInstance(nodeId, Version.of(4L));

    final var rootDirectory = tempDir.resolve("root");
    final var nodeDirectory = rootDirectory.resolve("node-1");

    createValidVersionDirectory(nodeDirectory, 1L, "file1.txt", "content1");

    // invalid version 2: directory exists but no initialization file
    final var invalidVersion = nodeDirectory.resolve("v2");
    Files.createDirectories(invalidVersion);
    Files.writeString(invalidVersion.resolve("file2.txt"), "content2");

    createValidVersionDirectory(nodeDirectory, 3L, "file3.txt", "content3");

    final DataDirectoryProvider initializer =
        new NodeIdBasedDataDirectoryProvider(
            OBJECT_MAPPER, nodeInstance, new RecordingDataDirectoryCopier());

    // when
    final var result = initializer.initialize(rootDirectory).join();

    // then
    assertThat(result).isEqualTo(nodeDirectory.resolve("v4"));

    assertThat(result.resolve("file3.txt")).exists();
    assertThat(Files.readString(result.resolve("file3.txt"))).isEqualTo("content3");
    assertThat(result.resolve("file2.txt")).doesNotExist();

    final var initInfo = readInitializationInfo(result);
    assertThat(initInfo.get("initializedFrom").asLong()).isEqualTo(3L);
  }

  @Test
  void shouldThrowExceptionIfAlreadyInitialized() throws Exception {
    // given
    final var nodeId = 1;
    final var nodeInstance = new NodeInstance(nodeId, Version.of(1L));

    final var rootDirectory = tempDir.resolve("root");
    final var targetDirectory = rootDirectory.resolve("node-1").resolve("v1");

    Files.createDirectories(targetDirectory);
    Files.writeString(targetDirectory.resolve("existing-file.txt"), "existing content");
    writeInitializationFile(targetDirectory, null);

    final DataDirectoryProvider initializer =
        new NodeIdBasedDataDirectoryProvider(
            OBJECT_MAPPER, nodeInstance, new RecordingDataDirectoryCopier());

    // when
    final var result = initializer.initialize(rootDirectory);

    // then
    assertThat(result)
        .completesExceptionallyWithin(Duration.ofSeconds(5))
        .withThrowableThat()
        .withMessageContaining(
            "Expected directory to not be initialized, but found valid init file in directory");
  }

  @Test
  void shouldCopyDirectoryStructureRecursively() throws Exception {
    // given
    final var nodeId = 1;
    final var nodeInstance = new NodeInstance(nodeId, Version.of(2L));

    final var rootDirectory = tempDir.resolve("root");
    final var previousVersion = rootDirectory.resolve("node-1").resolve("v1");

    Files.createDirectories(previousVersion.resolve("subdir1/subdir2"));
    Files.writeString(previousVersion.resolve("root-file.txt"), "root content");
    Files.writeString(previousVersion.resolve("subdir1/file1.txt"), "file1 content");
    Files.writeString(previousVersion.resolve("subdir1/subdir2/file2.txt"), "file2 content");
    writeInitializationFile(previousVersion, null);

    final DataDirectoryProvider initializer =
        new NodeIdBasedDataDirectoryProvider(
            OBJECT_MAPPER, nodeInstance, new RecordingDataDirectoryCopier());

    // when
    final var result = initializer.initialize(rootDirectory).join();

    // then
    assertThat(result.resolve("root-file.txt")).exists();
    assertThat(result.resolve("subdir1/file1.txt")).exists();
    assertThat(result.resolve("subdir1/subdir2/file2.txt")).exists();

    assertThat(Files.readString(result.resolve("root-file.txt"))).isEqualTo("root content");
    assertThat(Files.readString(result.resolve("subdir1/file1.txt"))).isEqualTo("file1 content");
    assertThat(Files.readString(result.resolve("subdir1/subdir2/file2.txt")))
        .isEqualTo("file2 content");

    final var initInfo = readInitializationInfo(result);
    assertThat(initInfo.get("initializedFrom").asLong()).isEqualTo(1L);
  }

  @Test
  void shouldHandleVersionZeroAsPrevious() throws Exception {
    // given
    final var nodeId = 1;
    final var nodeInstance = new NodeInstance(nodeId, Version.of(1L));

    final var rootDirectory = tempDir.resolve("root");
    final var nodeDirectory = rootDirectory.resolve("node-1");

    createValidVersionDirectory(nodeDirectory, 0L, "file0.txt", "version 0 content");

    final DataDirectoryProvider initializer =
        new NodeIdBasedDataDirectoryProvider(
            OBJECT_MAPPER, nodeInstance, new RecordingDataDirectoryCopier());

    // when
    final var result = initializer.initialize(rootDirectory).join();

    // then
    assertThat(result).isEqualTo(nodeDirectory.resolve("v1"));
    assertThat(result.resolve("file0.txt")).exists();
    assertThat(Files.readString(result.resolve("file0.txt"))).isEqualTo("version 0 content");

    final var initInfo = readInitializationInfo(result);
    assertThat(initInfo.get("initializedFrom").asLong()).isEqualTo(0L);
  }

  @Test
  void shouldDeleteAndReinitializeWhenTargetExistsButNotInitialized() throws Exception {
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

    final DataDirectoryProvider initializer =
        new NodeIdBasedDataDirectoryProvider(
            OBJECT_MAPPER, nodeInstance, new RecordingDataDirectoryCopier());

    // when
    final var result = initializer.initialize(rootDirectory).join();

    // then
    assertThat(result).isEqualTo(targetDirectory);
    assertThat(result.resolve("garbage.txt")).doesNotExist();
    assertThat(result.resolve("file1.txt")).exists();

    final var initInfo = readInitializationInfo(result);
    assertThat(initInfo.get("initializedFrom").asLong()).isEqualTo(1L);
  }

  @Test
  void shouldFailWhenNodeInstanceIsNull() {
    // given
    final var baseDirectory = tempDir.resolve("base");
    final DataDirectoryProvider initializer =
        new NodeIdBasedDataDirectoryProvider(
            OBJECT_MAPPER, null, new RecordingDataDirectoryCopier());

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
    writeInitializationFile(versionDir, null);
  }

  private static void writeInitializationFile(final Path directory, final Long initializedFrom)
      throws IOException {
    final var initFile = directory.resolve(DIRECTORY_INITIALIZED_FILE);

    final var initInfo =
        initializedFrom != null
            ? OBJECT_MAPPER
                .createObjectNode()
                .put("initialized", System.currentTimeMillis())
                .put("initializedFrom", initializedFrom)
            : OBJECT_MAPPER
                .createObjectNode()
                .put("initialized", System.currentTimeMillis())
                .putNull("initializedFrom");

    OBJECT_MAPPER.writeValue(initFile.toFile(), initInfo);
  }

  private static JsonNode readInitializationInfo(final Path directory) throws IOException {
    final Path initFile = directory.resolve(DIRECTORY_INITIALIZED_FILE);
    return OBJECT_MAPPER.readTree(initFile.toFile());
  }

  private static final class RecordingDataDirectoryCopier implements DataDirectoryCopier {

    private final List<Invocation> invocations = new ArrayList<>();

    List<Invocation> invocations() {
      return invocations;
    }

    @Override
    public void copy(final Path source, final Path target, final String markerFileName)
        throws IOException {
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
