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
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A data directory provider that uses the nodeId and nodeVersion to provide a versioned data
 * directory under a shared root. If a previous version exists and is marked as initialized, it will
 * initialize the new version by copying from it.
 */
public class NodeIdBasedDataDirectoryProvider implements DataDirectoryProvider {

  private static final Logger LOG = LoggerFactory.getLogger(NodeIdBasedDataDirectoryProvider.class);

  private static final String NODE_DIRECTORY_PREFIX = "node-";
  private static final String VERSION_DIRECTORY_PREFIX = "v";
  private static final String DIRECTORY_INITIALIZED_FILE = "directory-initialized.json";

  private final ObjectMapper objectMapper;
  private final NodeInstance nodeInstance;
  private final DataDirectoryCopier copier;

  public NodeIdBasedDataDirectoryProvider(
      final ObjectMapper objectMapper,
      final NodeInstance nodeInstance,
      final DataDirectoryCopier copier) {
    this.objectMapper = objectMapper;
    this.nodeInstance = nodeInstance;
    this.copier = copier;
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
      final var dataDirectory = nodeDirectory.resolve(VERSION_DIRECTORY_PREFIX + nodeVersion);

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

      final var previousVersion = findLatestValidPreviousVersion(nodeDirectory, nodeVersion);
      if (previousVersion.isPresent()) {
        final var previousDataDirectory =
            nodeDirectory.resolve(VERSION_DIRECTORY_PREFIX + previousVersion.get().version());

        LOG.info(
            "Initializing data directory {} by copying from {}",
            dataDirectory,
            previousDataDirectory);

        copier.copy(previousDataDirectory, dataDirectory, DIRECTORY_INITIALIZED_FILE);

        copier.validate(previousDataDirectory, dataDirectory, DIRECTORY_INITIALIZED_FILE);
        writeDirectoryInitializedFile(dataDirectory, previousVersion.get());
      } else {
        LOG.info(
            "No valid previous version found for node {}, creating empty directory {}",
            nodeId,
            dataDirectory);

        Files.createDirectories(dataDirectory);
        writeDirectoryInitializedFile(dataDirectory, null);
      }

      return CompletableFuture.completedFuture(dataDirectory);
    } catch (final Exception e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  private Optional<Version> findLatestValidPreviousVersion(
      final Path nodeDirectory, final long currentNodeVersion) {
    if (!Files.exists(nodeDirectory)) {
      return Optional.empty();
    }

    try (final var entries = Files.list(nodeDirectory)) {
      return entries
          .filter(Files::isDirectory)
          .map(Path::getFileName)
          .map(Path::toString)
          .filter(name -> name.startsWith(VERSION_DIRECTORY_PREFIX))
          .map(this::parseVersion)
          .flatMap(Optional::stream)
          .filter(version -> version < currentNodeVersion)
          .sorted(Comparator.reverseOrder())
          .filter(
              version ->
                  isDirectoryInitialized(nodeDirectory.resolve(VERSION_DIRECTORY_PREFIX + version)))
          .findFirst()
          .map(Version::of);
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to list node directory " + nodeDirectory, e);
    }
  }

  private Optional<Long> parseVersion(final String directoryName) {
    final var suffix = directoryName.substring(VERSION_DIRECTORY_PREFIX.length());
    try {
      return Optional.of(Long.parseLong(suffix));
    } catch (final NumberFormatException e) {
      return Optional.empty();
    }
  }

  private boolean isDirectoryInitialized(final Path directory) {
    final var initFile = directory.resolve(DIRECTORY_INITIALIZED_FILE);
    if (Files.exists(initFile) && Files.isRegularFile(initFile)) {
      try {
        final var file = initFile.toFile();
        final var info = objectMapper.readValue(file, DirectoryInitializationInfo.class);
        return info.initialized() > 0;
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
      final Path dataDirectory, final Version copiedFromVersion) throws IOException {
    final var initInfo = DirectoryInitializationInfo.copiedFrom(copiedFromVersion);

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
