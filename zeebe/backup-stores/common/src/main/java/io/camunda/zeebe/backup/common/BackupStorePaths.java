/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.common;

import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.api.BackupIdentifierWildcard;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Names and paths shared by all backup store implementations. They are part of the persisted layout
 * of existing backups, so changing any of them breaks reading backups taken before.
 *
 * <p>Stores can place all keys under a configured base path: normalized with {@link
 * #sanitizeBasePath(String)} when the configuration is read, then formatted once with {@link
 * #formatBasePath(Optional)} and passed to the path helpers as is.
 */
public final class BackupStorePaths {

  public static final String SNAPSHOT_FILESET_NAME = "snapshot";
  public static final String SEGMENTS_FILESET_NAME = "segments";
  public static final String METADATA_DIRECTORY_NAME = "metadata";
  public static final String METADATA_OBJECT_NAME = "metadata.json";
  public static final String MANIFESTS_DIRECTORY_NAME = "manifests";
  public static final String MANIFEST_OBJECT_NAME = "manifest.json";
  public static final String CONTENTS_DIRECTORY_NAME = "contents";

  private static final String NO_BASE_PATH = "";

  private BackupStorePaths() {}

  /**
   * Normalizes a configured base path by trimming it and removing all leading and trailing {@code
   * /}, so that keys can be joined to it with a single {@code /}. Empty if no base path is
   * configured, or if it is blank or a single {@code /}.
   *
   * @throws IllegalArgumentException if nothing remains after removing the slashes
   */
  public static Optional<String> sanitizeBasePath(final @Nullable String basePath) {
    if (basePath == null) {
      return Optional.empty();
    }

    var sanitized = basePath.trim();
    if (sanitized.isEmpty() || sanitized.equals("/")) {
      return Optional.empty();
    }

    while (sanitized.startsWith("/")) {
      sanitized = sanitized.substring(1);
    }
    while (sanitized.endsWith("/")) {
      sanitized = sanitized.substring(0, sanitized.length() - 1);
    }

    if (sanitized.isBlank()) {
      throw new IllegalArgumentException(
          "After removing leading and trailing '/' characters from basePath '%s', the remainder is empty and not a valid base path"
              .formatted(basePath));
    }
    return Optional.of(sanitized);
  }

  /**
   * Formats a base path normalized by {@link #sanitizeBasePath(String)} for the path helpers: the
   * base path followed by a single {@code /}, or empty without a base path.
   */
  public static String formatBasePath(final Optional<String> sanitizedBasePath) {
    return sanitizedBasePath.map(path -> path + "/").orElse(NO_BASE_PATH);
  }

  /** Returns the key of the backup metadata object of the given partition. */
  public static String metadataPath(final int partitionId) {
    return metadataPath(NO_BASE_PATH, partitionId);
  }

  /**
   * Returns the key of the backup metadata object of the given partition, under the given base
   * path.
   */
  public static String metadataPath(final String basePath, final int partitionId) {
    return "%s%s/%d/%s"
        .formatted(basePath, METADATA_DIRECTORY_NAME, partitionId, METADATA_OBJECT_NAME);
  }

  /** Returns the key of the manifest object of the given backup. */
  public static String manifestPath(final BackupIdentifier id) {
    return manifestPath(NO_BASE_PATH, id);
  }

  /** Returns the key of the manifest object of the given backup, under the given base path. */
  public static String manifestPath(final String basePath, final BackupIdentifier id) {
    return "%s%s/%s/%s/%s/%s"
        .formatted(
            basePath,
            MANIFESTS_DIRECTORY_NAME,
            id.partitionId(),
            id.checkpointId(),
            id.brokerId().id(),
            MANIFEST_OBJECT_NAME);
  }

  /**
   * Returns the longest key prefix shared by all manifests matching the given wildcard, to narrow
   * down listings. Keys starting with it do not necessarily match the wildcard.
   */
  public static String manifestsPrefix(final BackupIdentifierWildcard wildcard) {
    return manifestsPrefix(NO_BASE_PATH, wildcard);
  }

  /**
   * Returns the longest key prefix shared by all manifests matching the given wildcard under the
   * given base path, to narrow down listings. Keys starting with it do not necessarily match the
   * wildcard.
   */
  public static String manifestsPrefix(
      final String basePath, final BackupIdentifierWildcard wildcard) {
    return basePath + MANIFESTS_DIRECTORY_NAME + "/" + BackupIdentifierWildcard.asPrefix(wildcard);
  }

  /**
   * Returns the key prefix, ending with a {@code /}, under which the files of the given file set of
   * the given backup are stored.
   */
  public static String fileSetPath(final BackupIdentifier id, final String fileSetName) {
    return fileSetPath(NO_BASE_PATH, id, fileSetName);
  }

  /**
   * Returns the key prefix, ending with a {@code /}, under which the files of the given file set of
   * the given backup are stored, under the given base path.
   */
  public static String fileSetPath(
      final String basePath, final BackupIdentifier id, final String fileSetName) {
    return "%s%s/%s/%s/%s/%s/"
        .formatted(
            basePath,
            CONTENTS_DIRECTORY_NAME,
            id.partitionId(),
            id.checkpointId(),
            id.brokerId().id(),
            fileSetName);
  }
}
