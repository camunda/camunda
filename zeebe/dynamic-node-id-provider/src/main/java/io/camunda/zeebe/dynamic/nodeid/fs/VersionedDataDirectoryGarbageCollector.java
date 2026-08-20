/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.nodeid.fs;

import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Garbage collector for versioned data directories. Removes old version directories while retaining
 * a configurable number of valid (initialized) versions.
 */
public class VersionedDataDirectoryGarbageCollector {

  private static final Logger LOG =
      LoggerFactory.getLogger(VersionedDataDirectoryGarbageCollector.class);

  private final VersionedDirectoryLayout layout;
  private final int retentionCount;

  /**
   * Creates a new garbage collector.
   *
   * @param layout the versioned directory layout for the node directory
   * @param retentionCount the number of valid version directories to retain (must be at least 1)
   */
  public VersionedDataDirectoryGarbageCollector(
      final VersionedDirectoryLayout layout, final int retentionCount) {
    if (retentionCount < 1) {
      throw new IllegalArgumentException(
          "retentionCount must be at least 1, was " + retentionCount);
    }
    this.layout = layout;
    this.retentionCount = retentionCount;
  }

  /**
   * Collects (deletes) old version directories in the node directory, keeping at least {@code
   * retentionCount} valid (initialized) directories. Invalid directories are always deleted.
   *
   * @return the list of deleted directories
   */
  public List<Path> collect() {
    if (!Files.exists(layout.nodeDirectory())) {
      throw new IllegalArgumentException(
          "Node directory does not exist: " + layout.nodeDirectory());
    }

    final var versions = layout.findAllVersions();
    if (versions.isEmpty()) {
      LOG.debug("No version directories found in {}", layout.nodeDirectory());
      return List.of();
    }

    // Versions are sorted descending (newest first)
    // Keep track of valid directories and collect directories to delete
    final List<Path> directoriesToDelete = new ArrayList<>();
    int validCount = 0;

    for (final var version : versions) {
      final Path versionDir = layout.resolveVersionDirectory(version);

      if (layout.isDirectoryInitialized(version)) {
        validCount++;
        if (validCount > retentionCount) {
          // We have enough valid directories, this one can be deleted
          directoriesToDelete.add(versionDir);
        }
      } else {
        // Invalid directories are always deleted
        LOG.debug("Version directory {} is invalid and will be deleted", versionDir);
        directoriesToDelete.add(versionDir);
      }
    }

    if (directoriesToDelete.isEmpty()) {
      LOG.debug(
          "Found {} valid versions in {}, retention count is {}, nothing to collect",
          validCount,
          layout.nodeDirectory(),
          retentionCount);
      return List.of();
    }

    LOG.info(
        "Garbage collecting {} version directories in {} (keeping {} valid versions)",
        directoriesToDelete.size(),
        layout.nodeDirectory(),
        Math.min(validCount, retentionCount));

    return directoriesToDelete.stream().filter(this::deleteDirectory).toList();
  }

  private boolean deleteDirectory(final Path directory) {
    try {
      LOG.debug("Deleting version directory {}", directory);
      FileUtil.deleteFolder(directory);
      LOG.info("Deleted version directory {}", directory);
      return true;
    } catch (final IOException e) {
      LOG.warn("Failed to delete version directory {}", directory, e);
      return false;
    }
  }
}
