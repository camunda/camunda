/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.nodeid.fs;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.dynamic.nodeid.Version;
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents the layout of versioned data directories within a node directory. Responsible for
 * parsing, resolving, and listing version directories.
 *
 * <p>The directory structure on disk looks like:
 *
 * <pre>
 * nodeDirectory/
 * ├── v1/
 * │   ├── directory-initialized.json
 * │   └── ...
 * ├── v2/
 * │   ├── directory-initialized.json
 * │   └── ...
 * └── v3/
 *     ├── directory-initialized.json
 *     └── ...
 * </pre>
 *
 * <p>Each version directory (v1, v2, v3, ...) contains the data for that version of the node.
 */
public final class VersionedDirectoryLayout {

  public static final String DIRECTORY_INITIALIZED_FILE = "directory-initialized.json";
  private static final Logger LOG = LoggerFactory.getLogger(VersionedDirectoryLayout.class);
  private static final String VERSION_DIRECTORY_PREFIX = "v";
  private final Path nodeDirectory;

  private final ObjectMapper objectMapper;

  public VersionedDirectoryLayout(final Path nodeDirectory, final ObjectMapper objectMapper) {
    this.nodeDirectory = nodeDirectory;
    this.objectMapper = objectMapper;
  }

  /**
   * Returns the node directory this layout is based on.
   *
   * @return the node directory path
   */
  public Path nodeDirectory() {
    return nodeDirectory;
  }

  /**
   * Resolves a version directory path from this node directory.
   *
   * @param version the version number
   * @return the path to the version directory (e.g., nodeDirectory/v123)
   */
  public Path resolveVersionDirectory(final Version version) {
    return resolveVersionDirectory(version.version());
  }

  /**
   * Resolves a version directory path from this node directory.
   *
   * @param version the version number
   * @return the path to the version directory (e.g., nodeDirectory/v123)
   */
  public Path resolveVersionDirectory(final long version) {
    return nodeDirectory.resolve(VERSION_DIRECTORY_PREFIX + version);
  }

  /**
   * Resolves the path to the initialization file within the specified version directory.
   *
   * @param version the version whose initialization file path needs to be resolved
   * @return the path to the initialization file within the specified version directory
   */
  public Path initializationFilePath(final Version version) {
    return resolveVersionDirectory(version).resolve(DIRECTORY_INITIALIZED_FILE);
  }

  /**
   * Finds all version numbers in this node directory, sorted in descending order (newest first).
   *
   * @return list of version numbers, sorted descending
   * @throws UncheckedIOException if the directory cannot be listed
   */
  public List<Version> findAllVersions() {
    try (final var entries = Files.list(nodeDirectory)) {
      return entries
          .filter(Files::isDirectory)
          .map(Path::getFileName)
          .map(Path::toString)
          .map(VersionedDirectoryLayout::parseVersion)
          .flatMap(Optional::stream)
          .sorted(Comparator.comparing(Version::version).reversed())
          .toList();
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to list node directory " + nodeDirectory, e);
    }
  }

  public void initializeDirectory(final Version currentVersion, final Version copiedFromVersion)
      throws IOException {
    final var initInfo = DirectoryInitializationInfo.copiedFrom(currentVersion, copiedFromVersion);

    final var bytes = objectMapper.writeValueAsBytes(initInfo);

    final var dataDirectory = resolveVersionDirectory(currentVersion.version());

    final var initFile = dataDirectory.resolve(DIRECTORY_INITIALIZED_FILE);

    Files.write(
        initFile,
        bytes,
        StandardOpenOption.CREATE_NEW,
        StandardOpenOption.WRITE,
        StandardOpenOption.SYNC);

    FileUtil.flushDirectory(dataDirectory);
  }

  /**
   * Parses a version number from a directory name.
   *
   * @param directoryName the directory name (e.g., "v123")
   * @return the parsed version number, or empty if the name doesn't match the expected format
   */
  public static Optional<Version> parseVersion(final String directoryName) {
    if (!directoryName.startsWith(VERSION_DIRECTORY_PREFIX)) {
      return Optional.empty();
    }
    final var suffix = directoryName.substring(VERSION_DIRECTORY_PREFIX.length());
    try {
      return Optional.of(Version.of(Long.parseLong(suffix)));
    } catch (final NumberFormatException e) {
      return Optional.empty();
    }
  }

  public boolean isDirectoryInitialized(final Version version) {
    final var directory = resolveVersionDirectory(version.version());
    final var initFile = directory.resolve(DIRECTORY_INITIALIZED_FILE);
    if (Files.exists(initFile) && Files.isRegularFile(initFile)) {
      try {
        final var file = initFile.toFile();
        final var info = objectMapper.readValue(file, DirectoryInitializationInfo.class);

        // Validate that the version in the file matches the directory's expected version
        final var directoryName = directory.getFileName().toString();
        final var expectedVersion = extractVersionFromDirectory(directoryName);
        if (info.version() == null || info.version().version() != expectedVersion.version()) {
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

  private Version extractVersionFromDirectory(final String directoryName) {
    return VersionedDirectoryLayout.parseVersion(directoryName)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Failed to parse version from directory name: " + directoryName));
  }
}
