/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.nodeid.fs;

import static io.camunda.zeebe.dynamic.nodeid.fs.VersionedDirectoryLayout.DIRECTORY_INITIALIZED_FILE;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.dynamic.nodeid.NodeInstance;
import io.camunda.zeebe.dynamic.nodeid.Version;
import io.camunda.zeebe.util.FileUtil;
import java.nio.file.Files;
import java.nio.file.Path;
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
      final var layout = new VersionedDirectoryLayout(nodeDirectory, objectMapper);
      final var dataDirectory = layout.resolveVersionDirectory(nodeVersion);

      if (layout.isDirectoryInitialized(nodeInstance.version())) {
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
        layout.initializeDirectory(nodeInstance.version(), previousVersion.get());

        // Run garbage collection after successful validation
        new VersionedDataDirectoryGarbageCollector(layout, retentionCount).collect();
      } else {
        LOG.info(
            "No valid previous version found for node {}, creating empty directory {}",
            nodeId,
            dataDirectory);

        Files.createDirectories(dataDirectory);
        layout.initializeDirectory(nodeInstance.version(), null);
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
        layout.findAllVersions().stream()
            .filter(version -> version.version() < currentNodeVersion)
            .toList();

    LOG.trace("Found {} version directories: {}", foundVersions.size(), foundVersions);

    return foundVersions.stream().filter(layout::isDirectoryInitialized).findFirst();
  }
}
