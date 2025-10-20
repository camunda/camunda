/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.bootstrap;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Strategy that initializes data directory by copying from the latest valid previous version, or
 * creates an empty directory if no valid previous version exists.
 */
public class CopyFromPreviousVersionStrategy implements DataDirectoryInitializationStrategy {
  private static final Logger LOG = LoggerFactory.getLogger(CopyFromPreviousVersionStrategy.class);
  private static final String DIRECTORY_INITIALIZED_FILE = "directory-initialized.json";
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public Path initializeDataDirectory(
      final String rootDataDirectory, final int nodeId, final long currentNodeVersion)
      throws IOException {
    final Path nodeDirectory = Path.of(rootDataDirectory, "node-" + nodeId);
    final var dataDirectory = nodeDirectory.resolve("v" + currentNodeVersion);
    if (Files.exists(dataDirectory) && isDirectoryInitialized(dataDirectory)) {
      LOG.info("Data directory {} already exists and is initialized", dataDirectory);
      return dataDirectory;
    }

    // Find the latest valid previous version
    final Long validPreviousVersion =
        findLatestValidPreviousVersion(nodeDirectory, currentNodeVersion);

    if (validPreviousVersion != null) {
      final Path previousDataDirectory = nodeDirectory.resolve("v" + validPreviousVersion);
      LOG.info(
          "Found valid previous version {} at {}, copying to {}",
          validPreviousVersion,
          previousDataDirectory,
          dataDirectory);

      // Create parent directories if they don't exist
      Files.createDirectories(dataDirectory.getParent());

      // TODO: Mark the previousDataDirectory as read-only to prevent writes during copying incase
      // the old broker is still running.

      makeDirectoryReadonly(previousDataDirectory);
      // Copy the previous directory to the current one
      copyDirectory(previousDataDirectory, dataDirectory);

      // Write the initialization file with information about the copy
      writeDirectoryInitializedFile(dataDirectory, validPreviousVersion);

      LOG.info(
          "Setup data directory at {} by copying from version {} : {}",
          dataDirectory,
          validPreviousVersion,
          previousDataDirectory);
    } else {
      LOG.info("No valid previous version found, creating empty directory at {}", dataDirectory);

      // Create empty directory and initialize
      Files.createDirectories(dataDirectory);
      writeDirectoryInitializedFile(dataDirectory, null);
    }
    return dataDirectory;
  }

  @Override
  public String getStrategyName() {
    return "CopyFromPreviousVersion";
  }

  private void makeDirectoryReadonly(final Path previousDataDirectory) {
    if (!previousDataDirectory.toFile().setReadOnly()) {
      LOG.warn("Failed to set directory {} readonly, continuing anyway.", previousDataDirectory);
    }
  }

  private Long findLatestValidPreviousVersion(
      final Path dataDirectoryPrefix, final long currentNodeVersion) {
    for (long version = currentNodeVersion - 1; version >= 0; version--) {
      final Path versionDirectory = dataDirectoryPrefix.resolve("v" + version);
      final boolean exists = Files.exists(versionDirectory);
      final boolean directoryInitialized = isDirectoryInitialized(versionDirectory);
      LOG.info(
          "Checking for previous version directory at {}. File exists = {}, isDirectoryInitialized= {}",
          versionDirectory,
          exists,
          directoryInitialized);
      if (exists && directoryInitialized) {
        LOG.debug("Found valid previous version: {}", version);
        return version;
      }
    }
    return null;
  }

  private boolean isDirectoryInitialized(final Path directory) {
    final Path initFile = directory.resolve(DIRECTORY_INITIALIZED_FILE);
    return Files.exists(initFile) && Files.isRegularFile(initFile);
  }

  private void copyDirectory(final Path source, final Path target) throws IOException {
    try (final Stream<Path> pathStream = Files.walk(source)) {
      pathStream.forEach(
          sourcePath -> {
            try {
              final Path targetPath = target.resolve(source.relativize(sourcePath));
              if (Files.isDirectory(sourcePath)) {
                if (!sourcePath.getFileName().toString().equals("runtime")) {
                  Files.createDirectories(targetPath);
                }
              } else {
                Files.copy(sourcePath, targetPath);
                FileUtil.flushDirectory(targetPath.getParent()); // Verify if this is required.
                LOG.info("Copying file {} to {}", sourcePath, targetPath);
              }
            } catch (final IOException e) {
              throw new RuntimeException("Failed to copy " + sourcePath + " to " + target, e);
            }
          });
    }
  }

  private void writeDirectoryInitializedFile(
      final Path dataDirectory, final Long initializedFromVersion) throws IOException {
    final DirectoryInitializationInfo initInfo =
        initializedFromVersion != null
            ? DirectoryInitializationInfo.copiedFrom(initializedFromVersion)
            : DirectoryInitializationInfo.createdEmpty();

    final Path initFile = dataDirectory.resolve(DIRECTORY_INITIALIZED_FILE);
    final var fileContent = objectMapper.writeValueAsBytes(initInfo);
    Files.write(initFile, fileContent, StandardOpenOption.CREATE, StandardOpenOption.SYNC);
    FileUtil.flushDirectory(dataDirectory);
    LOG.info("Written directory initialization file to {}", initFile);
  }
}
