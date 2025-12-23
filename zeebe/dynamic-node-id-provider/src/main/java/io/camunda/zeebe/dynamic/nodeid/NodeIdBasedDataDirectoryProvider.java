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
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
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
  private static final String RUNTIME_DIRECTORY = "runtime";

  private final NodeIdProvider nodeIdProvider;
  private final DataDirectoryCopier copier;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public NodeIdBasedDataDirectoryProvider(
      final NodeIdProvider nodeIdProvider, final DataDirectoryCopier copier) {
    this.nodeIdProvider = nodeIdProvider;
    this.copier = copier;
  }

  @Override
  public CompletableFuture<Path> initialize(final Path rootDataDirectory) {
    final NodeInstance nodeInstance = nodeIdProvider.currentNodeInstance();
    if (nodeInstance == null) {
      return CompletableFuture.failedFuture(
          new IllegalStateException("Node instance is not available"));
    }

    try {
      final int nodeId = nodeInstance.id();
      final long nodeVersion = nodeInstance.version().version();

      final Path nodeDirectory = rootDataDirectory.resolve(NODE_DIRECTORY_PREFIX + nodeId);
      final Path dataDirectory = nodeDirectory.resolve(VERSION_DIRECTORY_PREFIX + nodeVersion);

      if (isDirectoryInitialized(dataDirectory)) {
        return CompletableFuture.completedFuture(dataDirectory);
      }

      if (Files.exists(dataDirectory)) {
        LOG.warn(
            "Data directory {} exists but is not initialized; deleting and re-initializing",
            dataDirectory);
        FileUtil.deleteFolderIfExists(dataDirectory);
      }

      Files.createDirectories(nodeDirectory);

      final Optional<Long> previousVersion =
          findLatestValidPreviousVersion(nodeDirectory, nodeVersion);
      if (previousVersion.isPresent()) {
        final Path previousDataDirectory =
            nodeDirectory.resolve(VERSION_DIRECTORY_PREFIX + previousVersion.get());

        LOG.info(
            "Initializing data directory {} by copying from {}",
            dataDirectory,
            previousDataDirectory);

        copier.copy(previousDataDirectory, dataDirectory, DIRECTORY_INITIALIZED_FILE);

        validateCopy(previousDataDirectory, dataDirectory);
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

  private Optional<Long> findLatestValidPreviousVersion(
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
          .findFirst();
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to list node directory " + nodeDirectory, e);
    }
  }

  private Optional<Long> parseVersion(final String directoryName) {
    final String suffix = directoryName.substring(VERSION_DIRECTORY_PREFIX.length());
    try {
      return Optional.of(Long.parseLong(suffix));
    } catch (final NumberFormatException e) {
      return Optional.empty();
    }
  }

  private boolean isDirectoryInitialized(final Path directory) {
    final Path initFile = directory.resolve(DIRECTORY_INITIALIZED_FILE);
    return Files.exists(initFile) && Files.isRegularFile(initFile);
  }

  private void validateCopy(final Path source, final Path target) throws IOException {
    Files.walkFileTree(
        source,
        new SimpleFileVisitor<>() {
          @Override
          public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs)
              throws IOException {
            if (isRuntimeDirectory(source, dir)) {
              return FileVisitResult.SKIP_SUBTREE;
            }

            final var relative = source.relativize(dir);
            final var targetDir = target.resolve(relative);
            if (!Files.isDirectory(targetDir)) {
              throw new IOException(
                  "Copy validation failed: missing directory " + targetDir + " for source " + dir);
            }

            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs)
              throws IOException {
            final var relative = source.relativize(file);
            if (relative.getFileName().toString().equals(DIRECTORY_INITIALIZED_FILE)) {
              return FileVisitResult.CONTINUE;
            }

            if (isRuntimeDirectory(source, file)) {
              return FileVisitResult.CONTINUE;
            }

            final var targetFile = target.resolve(relative);
            if (!Files.isRegularFile(targetFile)) {
              throw new IOException(
                  "Copy validation failed: missing file " + targetFile + " for source " + file);
            }

            return FileVisitResult.CONTINUE;
          }
        });
  }


  private boolean isRuntimeDirectory(final Path root, final Path path) {
    final var relative = root.relativize(path);
    for (final var part : relative) {
      if (part.toString().equals(RUNTIME_DIRECTORY)) {
        return true;
      }
    }
    return false;
  }

  private void writeDirectoryInitializedFile(final Path dataDirectory, final Long copiedFromVersion)
      throws IOException {
    final DirectoryInitializationInfo initInfo =
        copiedFromVersion != null
            ? DirectoryInitializationInfo.copiedFrom(copiedFromVersion)
            : DirectoryInitializationInfo.createdEmpty();

    final var bytes = objectMapper.writeValueAsBytes(initInfo);

    final var initFile = dataDirectory.resolve(DIRECTORY_INITIALIZED_FILE);
    final var tmpFile = dataDirectory.resolve(DIRECTORY_INITIALIZED_FILE + ".tmp");

    Files.write(
        tmpFile,
        bytes,
        StandardOpenOption.CREATE_NEW,
        StandardOpenOption.WRITE,
        StandardOpenOption.SYNC);
    Files.move(
        tmpFile, initFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

    FileUtil.flushDirectory(dataDirectory);
  }

  private record DirectoryInitializationInfo(long initialized, Long initializedFrom) {

    static DirectoryInitializationInfo copiedFrom(final long version) {
      return new DirectoryInitializationInfo(System.currentTimeMillis(), version);
    }

    static DirectoryInitializationInfo createdEmpty() {
      return new DirectoryInitializationInfo(System.currentTimeMillis(), null);
    }
  }
}
