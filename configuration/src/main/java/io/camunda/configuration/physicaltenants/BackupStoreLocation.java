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
 * <p>Mirrors {@link DocumentStoreLocation}, except that {@code /} does bound a base path here — see
 * {@link #sharesKeySpaceWith}.
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

  /**
   * Region is excluded because S3 bucket names are globally unique across regions. The base path is
   * compared as configured, since {@code S3BackupConfig} rejects one that starts or ends with
   * {@code /} rather than accepting and trimming it.
   */
  private static BackupStoreLocation s3(final S3 s3) {
    return new BackupStoreLocation(
        "s3",
        List.of(
            DocumentStoreLocation.normalizeName(s3.getBucketName()),
            DocumentStoreLocation.normalizeEndpoint(s3.getEndpoint())),
        keyPrefixOf(s3.getBasePath()));
  }

  /**
   * The base path is stripped of surrounding {@code /} the way {@code
   * GcsBackupConfig#sanitizeBasePath} strips it, so that {@code /backups} and {@code backups} are
   * recognized as the one prefix they both resolve to.
   */
  private static BackupStoreLocation gcs(final Gcs gcs) {
    return new BackupStoreLocation(
        "gcs",
        List.of(
            DocumentStoreLocation.normalizeName(gcs.getBucketName()),
            DocumentStoreLocation.normalizeEndpoint(gcs.getHost())),
        stripSurroundingSlashes(keyPrefixOf(gcs.getBasePath())));
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
            DocumentStoreLocation.normalizeName(azure.getBasePath()),
            DocumentStoreLocation.normalizeEndpoint(azure.getEndpoint()),
            DocumentStoreLocation.normalizeName(azure.getAccountName())),
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
   * Whether the two stores could address a common key: whenever one base path contains the other,
   * equal paths included. A store at a bucket root is therefore not isolated from a base path
   * inside it.
   *
   * <p>The separator is what bounds a base path here, unlike in {@link DocumentStoreLocation}: a
   * backup key always continues as {@code <basePath>/<partitionId>/…}, and the stores build both
   * their keys and their list prefixes as {@code basePath + "/"}, so nothing a store writes or
   * lists can reach a sibling whose name merely starts with the same characters. {@code backups}
   * contains {@code backups/tenanta} but not {@code backups-tenanta}.
   */
  boolean sharesKeySpaceWith(final BackupStoreLocation other) {
    if (!store.equals(other.store) || !namespace.equals(other.namespace)) {
      return false;
    }
    if ("filesystem".equals(store)) {
      return pathContains(keyPrefix, other.keyPrefix) || pathContains(other.keyPrefix, keyPrefix);
    }
    return keyContains(keyPrefix, other.keyPrefix) || keyContains(other.keyPrefix, keyPrefix);
  }

  /** Whether every key below {@code inner} is also addressed by {@code outer}. */
  private static boolean keyContains(final String outer, final String inner) {
    return outer.isEmpty() || inner.equals(outer) || inner.startsWith(outer + "/");
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

  private static String keyPrefixOf(final @Nullable String basePath) {
    return Objects.requireNonNullElse(basePath, "").trim();
  }

  private static String stripSurroundingSlashes(final String basePath) {
    var stripped = basePath;
    while (stripped.startsWith("/")) {
      stripped = stripped.substring(1);
    }
    while (stripped.endsWith("/")) {
      stripped = stripped.substring(0, stripped.length() - 1);
    }
    return stripped;
  }
}
