/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CopyFromPreviousVersionStrategyTest {

  private static final String DIRECTORY_INITIALIZED_FILE = "directory-initialized.json";

  @TempDir Path tempDir;

  private CopyFromPreviousVersionStrategy strategy;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    strategy = new CopyFromPreviousVersionStrategy();
    objectMapper = new ObjectMapper();
  }

  @Test
  void shouldCreateEmptyDirectoryWhenNoPreviousVersionExists() throws IOException {
    // given
    final long currentNodeVersion = 1L;

    // when
    final Path result = strategy.initializeDataDirectory(tempDir.toString(), 1, currentNodeVersion);

    // then
    assertThat(result).isEqualTo(tempDir.resolve("node-1/v1"));
    assertThat(Files.exists(result)).isTrue();
    assertThat(Files.isDirectory(result)).isTrue();

    // Verify initialization file was created
    final Path initFile = result.resolve(DIRECTORY_INITIALIZED_FILE);
    assertThat(Files.exists(initFile)).isTrue();

    // Verify initialization file contents
    final DirectoryInitializationInfo initInfo =
        objectMapper.readValue(initFile.toFile(), DirectoryInitializationInfo.class);
    assertThat(initInfo.initialized()).isNotNull();
    assertThat(initInfo.initializedFrom()).isNull();
  }

  @Test
  void shouldCopyFromPreviousInitialized() throws IOException {
    // given
    final long previousVersion = 1L;
    strategy.initializeDataDirectory(tempDir.toString(), 1, previousVersion);

    // when

    final long currentVersion = 2L;
    final Path result = strategy.initializeDataDirectory(tempDir.toString(), 1, currentVersion);

    // Verify initialization file was created
    final Path initFile = result.resolve(DIRECTORY_INITIALIZED_FILE);
    assertThat(Files.exists(initFile)).isTrue();

    // Verify initialization file contents
    final DirectoryInitializationInfo initInfo =
        objectMapper.readValue(initFile.toFile(), DirectoryInitializationInfo.class);
    assertThat(initInfo.initialized()).isNotNull();
    assertThat(initInfo.initializedFrom()).isEqualTo(1);
  }

  @Test
  void shouldCopyFromLatestValidPreviousVersion() throws IOException {
    // given
    final Path dataDirectoryPrefix = tempDir.resolve("node-1");
    final long currentNodeVersion = 3L;

    // Create previous version directories with data
    createValidVersionDirectory(dataDirectoryPrefix, 0L, "file0.txt", "content0");
    createValidVersionDirectory(dataDirectoryPrefix, 2L, "file2.txt", "content2");

    // when
    final Path result = strategy.initializeDataDirectory(tempDir.toString(), 1, currentNodeVersion);

    // then
    assertThat(result).isEqualTo(dataDirectoryPrefix.resolve("v3"));
    assertThat(Files.exists(result)).isTrue();

    // Verify data was copied from version 2 (latest valid)
    final Path copiedFile = result.resolve("file2.txt");
    assertThat(Files.exists(copiedFile)).isTrue();
    assertThat(Files.readString(copiedFile)).isEqualTo("content2");

    // Verify initialization file was created with correct info
    final Path initFile = result.resolve(DIRECTORY_INITIALIZED_FILE);
    assertThat(Files.exists(initFile)).isTrue();

    final DirectoryInitializationInfo initInfo =
        objectMapper.readValue(initFile.toFile(), DirectoryInitializationInfo.class);
    assertThat(initInfo.initialized()).isNotNull();
    assertThat(initInfo.initializedFrom()).isEqualTo(2L);
  }

  @Test
  void shouldSkipInvalidPreviousVersionsAndUseLatestValid() throws IOException {
    // given
    final Path dataDirectoryPrefix = tempDir.resolve("node-1");
    final long currentNodeVersion = 4L;

    // Create valid version 1
    createValidVersionDirectory(dataDirectoryPrefix, 1L, "file1.txt", "content1");

    // Create invalid version 2 (directory exists but no initialization file)
    final Path invalidVersion = dataDirectoryPrefix.resolve("2");
    Files.createDirectories(invalidVersion);
    Files.writeString(invalidVersion.resolve("file2.txt"), "content2");
    // Note: no directory-initialized.json file

    // Create valid version 3
    createValidVersionDirectory(dataDirectoryPrefix, 3L, "file3.txt", "content3");

    // when
    final Path result = strategy.initializeDataDirectory(tempDir.toString(), 1, currentNodeVersion);

    // then
    assertThat(result).isEqualTo(dataDirectoryPrefix.resolve("v4"));

    // Verify data was copied from version 3 (latest valid), not version 2
    final Path copiedFile = result.resolve("file3.txt");
    assertThat(Files.exists(copiedFile)).isTrue();
    assertThat(Files.readString(copiedFile)).isEqualTo("content3");

    // Verify file from invalid version 2 was not copied
    assertThat(Files.exists(result.resolve("file2.txt"))).isFalse();

    // Verify initialization info
    final DirectoryInitializationInfo initInfo = readInitializationInfo(result);
    assertThat(initInfo.initializedFrom()).isEqualTo(3L);
  }

  @Test
  void shouldReturnExistingDirectoryWhenAlreadyInitialized() throws IOException {
    // given
    final Path dataDirectoryPrefix = tempDir.resolve("node-1");
    final long currentNodeVersion = 1L;
    final Path targetDirectory = dataDirectoryPrefix.resolve("v1");

    // Create already initialized directory
    Files.createDirectories(targetDirectory);
    Files.writeString(targetDirectory.resolve("existing-file.txt"), "existing content");
    writeInitializationFile(targetDirectory, null);

    // when
    final Path result = strategy.initializeDataDirectory(tempDir.toString(), 1, currentNodeVersion);

    // then
    assertThat(result).isEqualTo(targetDirectory);
    assertThat(Files.exists(result.resolve("existing-file.txt"))).isTrue();
    assertThat(Files.readString(result.resolve("existing-file.txt"))).isEqualTo("existing content");
  }

  @Test
  void shouldCopyDirectoryStructureRecursively() throws IOException {
    // given
    final Path dataDirectoryPrefix = tempDir.resolve("node-1");
    final long currentNodeVersion = 2L;

    // Create previous version with nested directory structure
    final Path previousVersion = dataDirectoryPrefix.resolve("v1");
    Files.createDirectories(previousVersion.resolve("subdir1/subdir2"));
    Files.writeString(previousVersion.resolve("root-file.txt"), "root content");
    Files.writeString(previousVersion.resolve("subdir1/file1.txt"), "file1 content");
    Files.writeString(previousVersion.resolve("subdir1/subdir2/file2.txt"), "file2 content");
    writeInitializationFile(previousVersion, null);

    // when
    final Path result = strategy.initializeDataDirectory(tempDir.toString(), 1, currentNodeVersion);

    // then
    assertThat(result).isEqualTo(dataDirectoryPrefix.resolve("v2"));

    // Verify all files and directories were copied
    assertThat(Files.exists(result.resolve("root-file.txt"))).isTrue();
    assertThat(Files.exists(result.resolve("subdir1/file1.txt"))).isTrue();
    assertThat(Files.exists(result.resolve("subdir1/subdir2/file2.txt"))).isTrue();

    assertThat(Files.readString(result.resolve("root-file.txt"))).isEqualTo("root content");
    assertThat(Files.readString(result.resolve("subdir1/file1.txt"))).isEqualTo("file1 content");
    assertThat(Files.readString(result.resolve("subdir1/subdir2/file2.txt")))
        .isEqualTo("file2 content");
  }

  @Test
  void shouldHandleVersionZero() throws IOException {
    // given
    final Path dataDirectoryPrefix = tempDir.resolve("node-1");
    final long currentNodeVersion = 1L;

    // Create version 0
    createValidVersionDirectory(dataDirectoryPrefix, 0L, "file0.txt", "version 0 content");

    // when
    final Path result = strategy.initializeDataDirectory(tempDir.toString(), 1, currentNodeVersion);

    // then
    assertThat(result).isEqualTo(dataDirectoryPrefix.resolve("v1"));
    assertThat(Files.exists(result.resolve("file0.txt"))).isTrue();
    assertThat(Files.readString(result.resolve("file0.txt"))).isEqualTo("version 0 content");

    final DirectoryInitializationInfo initInfo = readInitializationInfo(result);
    assertThat(initInfo.initializedFrom()).isEqualTo(0L);
  }

  @Test
  void shouldReturnCorrectStrategyName() {
    // when
    final String strategyName = strategy.getStrategyName();

    // then
    assertThat(strategyName).isEqualTo("CopyFromPreviousVersion");
  }

  @Test
  void shouldHandleIOExceptionDuringCopy() throws IOException {
    // given
    final Path dataDirectoryPrefix = tempDir.resolve("node-1");
    final long currentNodeVersion = 2L;

    // Create previous version
    final Path previousVersion = dataDirectoryPrefix.resolve("1");
    Files.createDirectories(previousVersion);
    writeInitializationFile(previousVersion, null);

    // Create a file that will cause issues during copy (simulate by making target read-only after
    // creation)
    Files.writeString(previousVersion.resolve("test-file.txt"), "content");

    // Make the temp directory read-only to cause copy failure
    tempDir.toFile().setReadOnly();

    try {
      // when/then
      assertThatThrownBy(
              () -> strategy.initializeDataDirectory(tempDir.toString(), 1, currentNodeVersion))
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("Failed to copy");
    } finally {
      // Cleanup: make directory writable again
      tempDir.toFile().setWritable(true);
    }
  }

  private void createValidVersionDirectory(
      final Path baseDir, final long version, final String fileName, final String content)
      throws IOException {
    final Path versionDir = baseDir.resolve("v" + version);
    Files.createDirectories(versionDir);
    Files.writeString(versionDir.resolve(fileName), content);
    writeInitializationFile(versionDir, null);
  }

  private void writeInitializationFile(final Path directory, final Long initializedFrom)
      throws IOException {
    final DirectoryInitializationInfo initInfo =
        initializedFrom != null
            ? DirectoryInitializationInfo.copiedFrom(initializedFrom)
            : DirectoryInitializationInfo.createdEmpty();

    final Path initFile = directory.resolve(DIRECTORY_INITIALIZED_FILE);
    objectMapper.writeValue(initFile.toFile(), initInfo);
  }

  private DirectoryInitializationInfo readInitializationInfo(final Path directory)
      throws IOException {
    final Path initFile = directory.resolve(DIRECTORY_INITIALIZED_FILE);
    return objectMapper.readValue(initFile.toFile(), DirectoryInitializationInfo.class);
  }
}
