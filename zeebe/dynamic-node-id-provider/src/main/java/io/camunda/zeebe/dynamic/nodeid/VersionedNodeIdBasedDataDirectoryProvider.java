/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.nodeid;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A data directory provider that uses the nodeId and nodeVersion to provide a versioned data
 * directory under a shared root. If a previous version exists and is marked as initialized, it will
 * initialize the new version by copying from it.
 */
public class VersionedNodeIdBasedDataDirectoryProvider implements DataDirectoryProvider {

  private static final Logger LOG =
      LoggerFactory.getLogger(VersionedNodeIdBasedDataDirectoryProvider.class);

  private static final String NODE_DIRECTORY_PREFIX = "node-";
  private static final String DIRECTORY_INITIALIZED_FILE = "directory-initialized.json";

  private final ObjectMapper objectMapper;
  private final NodeInstance nodeInstance;
  private final DataDirectoryCopier copier;
  private final boolean previousNodeGracefullyShutdown;
  private final int retentionCount;

  public VersionedNodeIdBasedDataDirectoryProvider(
      final ObjectMapper objectMapper,
      final NodeInstance nodeInstance,
      final DataDirectoryCopier copier,
      final boolean previousNodeGracefullyShutdown,
      final int retentionCount) {
    this.objectMapper = objectMapper;
    this.nodeInstance = nodeInstance;
    this.copier = copier;
    this.previousNodeGracefullyShutdown = previousNodeGracefullyShutdown;
    this.retentionCount = retentionCount;
  }

  @Override
  public CompletableFuture<Path> initialize(final Path rootDataDirectory) {
    if (nodeInstance == null) {
      return CompletableFuture.failedFuture(
          new IllegalStateException("Node instance is not available"));
    }

    try {
      final var nodeId = nodeInstance.id();
      final var nodeVersion = nodeInstance.version().version();

      final var nodeDirectory = rootDataDirectory.resolve(NODE_DIRECTORY_PREFIX + nodeId);
      final var layout = new VersionedDirectoryLayout(nodeDirectory);
      final var dataDirectory = layout.resolveVersionDirectory(nodeVersion);

      if (isDirectoryInitialized(dataDirectory)) {
        return CompletableFuture.failedFuture(
            new IllegalStateException(
                "Expected directory to not be initialized, but found valid init file in directory "
                    + dataDirectory));
      }

      if (Files.exists(dataDirectory)) {
        LOG.warn(
            "Data directory {} exists but is not initialized; deleting and re-initializing",
            dataDirectory);
        FileUtil.deleteFolderIfExists(dataDirectory);
      }

      Files.createDirectories(nodeDirectory);

      final var previousVersion = findLatestValidPreviousVersion(layout, nodeVersion);
      if (previousVersion.isPresent()) {
        final var previousDataDirectory =
            layout.resolveVersionDirectory(previousVersion.get().version());

        LOG.info(
            "Initializing data directory {} by copying from {}",
            dataDirectory,
            previousDataDirectory);

        copier.copy(
            previousDataDirectory,
            dataDirectory,
            DIRECTORY_INITIALIZED_FILE,
            previousNodeGracefullyShutdown);

        copier.validate(previousDataDirectory, dataDirectory, DIRECTORY_INITIALIZED_FILE);
        writeDirectoryInitializedFile(dataDirectory, nodeInstance.version(), previousVersion.get());

        // Run garbage collection after successful validation
        new VersionedDataDirectoryGarbageCollector(
                layout, retentionCount, this::isDirectoryInitialized)
            .collect();
      } else {
        LOG.info(
            "No valid previous version found for node {}, creating empty directory {}",
            nodeId,
            dataDirectory);

        Files.createDirectories(dataDirectory);
        writeDirectoryInitializedFile(dataDirectory, nodeInstance.version(), null);
      }

      return CompletableFuture.completedFuture(dataDirectory);
    } catch (final Exception e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  private Optional<Version> findLatestValidPreviousVersion(
      final VersionedDirectoryLayout layout, final long currentNodeVersion) {
    if (!Files.exists(layout.nodeDirectory())) {
      return Optional.empty();
    }

    final var foundVersions =
        layout.findAllVersions().stream().filter(version -> version < currentNodeVersion).toList();

    LOG.trace("Found {} version directories: {}", foundVersions.size(), foundVersions);

    return foundVersions.stream()
        .filter(version -> isDirectoryInitialized(layout.resolveVersionDirectory(version)))
        .findFirst()
        .map(Version::of);
  }

  private boolean isDirectoryInitialized(final Path directory) {
    final var initFile = directory.resolve(DIRECTORY_INITIALIZED_FILE);
    if (Files.exists(initFile) && Files.isRegularFile(initFile)) {
      try {
        final var file = initFile.toFile();
        final var info = objectMapper.readValue(file, DirectoryInitializationInfo.class);

        // Validate that initializedAt is set
        if (info.initializedAt() <= 0) {
          return false;
        }

        // Validate that the version in the file matches the directory's expected version
        final var directoryName = directory.getFileName().toString();
        final var expectedVersion =
            VersionedDirectoryLayout.parseVersion(directoryName)
                .orElseThrow(
                    () ->
                        new IllegalStateException(
                            "Failed to parse version from directory name: " + directoryName));
        if (info.version() == null || info.version().version() != expectedVersion) {
          LOG.warn(
              "Version mismatch in init file at {}: expected {}, found {}",
              initFile,
              expectedVersion,
              info.version() != null ? info.version().version() : "null");
          return false;
        }

        return true;
      } catch (final Exception e) {
        LOG.warn(
            "Failed to open file at path {}, marking directory as not correctly initialized",
            initFile,
            e);
      }
    }
    return false;
  }

  private void writeDirectoryInitializedFile(
      final Path dataDirectory, final Version currentVersion, final Version copiedFromVersion)
      throws IOException {
    final var initInfo = DirectoryInitializationInfo.copiedFrom(currentVersion, copiedFromVersion);

    final var bytes = objectMapper.writeValueAsBytes(initInfo);

    final var initFile = dataDirectory.resolve(DIRECTORY_INITIALIZED_FILE);

    Files.write(
        initFile,
        bytes,
        StandardOpenOption.CREATE_NEW,
        StandardOpenOption.WRITE,
        StandardOpenOption.SYNC);

    FileUtil.flushDirectory(dataDirectory);
  }
}
