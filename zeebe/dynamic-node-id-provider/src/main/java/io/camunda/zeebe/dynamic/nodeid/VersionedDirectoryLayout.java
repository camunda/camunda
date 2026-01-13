/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.nodeid;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

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

  private static final String VERSION_DIRECTORY_PREFIX = "v";

  private final Path nodeDirectory;

  public VersionedDirectoryLayout(final Path nodeDirectory) {
    this.nodeDirectory = nodeDirectory;
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
  public Path resolveVersionDirectory(final long version) {
    return nodeDirectory.resolve(VERSION_DIRECTORY_PREFIX + version);
  }

  /**
   * Finds all version numbers in this node directory, sorted in descending order (newest first).
   *
   * @return list of version numbers, sorted descending
   * @throws UncheckedIOException if the directory cannot be listed
   */
  public List<Long> findAllVersions() {
    try (final var entries = Files.list(nodeDirectory)) {
      return entries
          .filter(Files::isDirectory)
          .map(Path::getFileName)
          .map(Path::toString)
          .map(VersionedDirectoryLayout::parseVersion)
          .flatMap(Optional::stream)
          .sorted(Comparator.reverseOrder())
          .toList();
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to list node directory " + nodeDirectory, e);
    }
  }

  /**
   * Parses a version number from a directory name.
   *
   * @param directoryName the directory name (e.g., "v123")
   * @return the parsed version number, or empty if the name doesn't match the expected format
   */
  public static Optional<Long> parseVersion(final String directoryName) {
    if (!directoryName.startsWith(VERSION_DIRECTORY_PREFIX)) {
      return Optional.empty();
    }
    final var suffix = directoryName.substring(VERSION_DIRECTORY_PREFIX.length());
    try {
      return Optional.of(Long.parseLong(suffix));
    } catch (final NumberFormatException e) {
      return Optional.empty();
    }
  }
}
