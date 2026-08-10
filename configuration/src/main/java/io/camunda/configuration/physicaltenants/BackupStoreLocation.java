/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import io.camunda.configuration.Azure;
import io.camunda.configuration.Filesystem;
import io.camunda.configuration.Gcs;
import io.camunda.configuration.PrimaryStorageBackup;
import io.camunda.configuration.S3;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The key space a primary-storage backup store occupies: a {@code namespace} no key can escape —
 * bucket, container or directory — and the {@code keyPrefix} every key inside it starts with.
 *
 * <p>Backup keys are addressed by {@code partitionId/checkpointId/nodeId} alone; nothing in them
 * names a physical tenant, and partition ids restart at 1 in every tenant's partition group. Two
 * tenants pointed at one key space therefore write their partition 1 backups to the same keys, and
 * each tenant's retention lists and deletes by partition wildcard, so it also deletes the other
 * tenant's backups. Comparing key spaces is what keeps that from being expressible.
 *
 * <p>Mirrors {@link DocumentStoreLocation}, including its treatment of {@code /} as an ordinary
 * character in object keys rather than a boundary.
 */
@NullMarked
record BackupStoreLocation(String store, List<String> namespace, String keyPrefix) {

  static @Nullable BackupStoreLocation of(final PrimaryStorageBackup backup) {
    return switch (backup.getStore()) {
      // no store, no keys, nothing to collide with
      case NONE -> null;
      case S3 -> s3(backup.getS3());
      case GCS -> gcs(backup.getGcs());
      case AZURE -> azure(backup.getAzure());
      case FILESYSTEM -> filesystem(backup.getFilesystem());
    };
  }

  /** Region is excluded because S3 bucket names are globally unique across regions. */
  private static BackupStoreLocation s3(final S3 s3) {
    return new BackupStoreLocation(
        "s3",
        List.of(normalize(s3.getBucketName()), normalize(s3.getEndpoint())),
        keyPrefixOf(s3.getBasePath()));
  }

  private static BackupStoreLocation gcs(final Gcs gcs) {
    return new BackupStoreLocation(
        "gcs",
        List.of(normalize(gcs.getBucketName()), normalize(gcs.getHost())),
        keyPrefixOf(gcs.getBasePath()));
  }

  /**
   * Azure has no prefix to compare: {@code basePath} names the container itself (see {@code
   * AzureBackupStoreConfig#toStoreConfig}), and containers do not nest, so two tenants either share
   * one container or are isolated by construction.
   */
  private static BackupStoreLocation azure(final Azure azure) {
    return new BackupStoreLocation(
        "azure",
        List.of(
            normalize(azure.getBasePath()),
            normalize(azure.getEndpoint()),
            normalize(azure.getAccountName())),
        "");
  }

  /**
   * The whole directory tree below {@code basePath} belongs to one store, so a tenant configured
   * inside another tenant's tree is not isolated from it. The path is normalized so that {@code
   * /backups/./a} and {@code /backups/a} are recognized as one location, but it is not resolved
   * against the file system: symlinks and relative paths interpreted from different working
   * directories are out of reach of a static check.
   */
  private static BackupStoreLocation filesystem(final Filesystem filesystem) {
    return new BackupStoreLocation(
        "filesystem", List.of(), normalizePath(filesystem.getBasePath()));
  }

  /**
   * Whether the two stores could address a common key: whenever one key prefix contains the other,
   * equal prefixes included.
   *
   * <p>For object stores a separator bounds nothing — {@code /} is an ordinary character in S3 keys
   * and GCS object names — so a store at a bucket root is not isolated from a base path inside it.
   * For the file system the containment is checked per path segment instead, because there {@code
   * /backups} and {@code /backups-archive} really are two directories.
   */
  boolean sharesKeySpaceWith(final BackupStoreLocation other) {
    if (!store.equals(other.store) || !namespace.equals(other.namespace)) {
      return false;
    }
    if ("filesystem".equals(store)) {
      return pathContains(keyPrefix, other.keyPrefix) || pathContains(other.keyPrefix, keyPrefix);
    }
    return keyPrefix.startsWith(other.keyPrefix) || other.keyPrefix.startsWith(keyPrefix);
  }

  String describe() {
    final var location =
        namespace.isEmpty() ? keyPrefix : namespace + ", basePath='" + keyPrefix + "'";
    return String.format("store=%s, %s", store, location);
  }

  private static boolean pathContains(final String outer, final String inner) {
    try {
      return Path.of(inner).startsWith(Path.of(outer));
    } catch (final InvalidPathException e) {
      // a path this platform cannot parse is compared as the plain string it was configured as
      return inner.equals(outer);
    }
  }

  private static String normalizePath(final @Nullable String basePath) {
    if (basePath == null || basePath.isBlank()) {
      return "";
    }
    try {
      return Path.of(basePath).normalize().toString();
    } catch (final InvalidPathException e) {
      return basePath.trim();
    }
  }

  /** Bucket and container names are restricted to lower case, so folding case cannot merge two. */
  private static String normalize(final @Nullable String value) {
    return value == null ? "" : value.trim().toLowerCase();
  }

  private static String keyPrefixOf(final @Nullable String basePath) {
    return Objects.requireNonNullElse(basePath, "").trim();
  }
}
