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

/**
 * Names and paths shared by all backup store implementations. They are part of the persisted layout
 * of existing backups, so changing any of them breaks reading backups taken before.
 */
public final class BackupStorePaths {

  public static final String SNAPSHOT_FILESET_NAME = "snapshot";
  public static final String SEGMENTS_FILESET_NAME = "segments";
  public static final String METADATA_DIRECTORY_NAME = "metadata";
  public static final String METADATA_OBJECT_NAME = "metadata.json";
  public static final String MANIFESTS_DIRECTORY_NAME = "manifests";
  public static final String MANIFEST_OBJECT_NAME = "manifest.json";
  public static final String CONTENTS_DIRECTORY_NAME = "contents";

  private BackupStorePaths() {}

  /**
   * Returns the key of the backup metadata object of the given partition.
   *
   * @param prefix prepended as is; either empty or ending with a {@code /}
   */
  public static String metadataPath(final String prefix, final int partitionId) {
    return "%s%s/%d/%s"
        .formatted(prefix, METADATA_DIRECTORY_NAME, partitionId, METADATA_OBJECT_NAME);
  }

  /**
   * Returns the key of the manifest object of the given backup.
   *
   * @param prefix prepended as is; either empty or ending with a {@code /}
   */
  public static String manifestPath(final String prefix, final BackupIdentifier id) {
    return "%s%s/%s/%s/%s/%s"
        .formatted(
            prefix,
            MANIFESTS_DIRECTORY_NAME,
            id.partitionId(),
            id.checkpointId(),
            id.brokerId().id(),
            MANIFEST_OBJECT_NAME);
  }

  /**
   * Returns the longest key prefix shared by all manifests matching the given wildcard, to narrow
   * down listings. Keys starting with it do not necessarily match the wildcard.
   *
   * @param prefix prepended as is; either empty or ending with a {@code /}
   */
  public static String manifestsPrefix(
      final String prefix, final BackupIdentifierWildcard wildcard) {
    return prefix + MANIFESTS_DIRECTORY_NAME + "/" + BackupIdentifierWildcard.asPrefix(wildcard);
  }

  /**
   * Returns the key prefix, ending with a {@code /}, under which the files of the given file set of
   * the given backup are stored.
   *
   * @param prefix prepended as is; either empty or ending with a {@code /}
   */
  public static String fileSetPath(
      final String prefix, final BackupIdentifier id, final String fileSetName) {
    return "%s%s/%s/%s/%s/%s/"
        .formatted(
            prefix,
            CONTENTS_DIRECTORY_NAME,
            id.partitionId(),
            id.checkpointId(),
            id.brokerId().id(),
            fileSetName);
  }
}
