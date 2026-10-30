/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.common;

/**
 * Names and paths shared by all backup store implementations. They are part of the persisted layout
 * of existing backups, so changing any of them breaks reading backups taken before.
 */
public final class BackupStorePaths {

  public static final String SNAPSHOT_FILESET_NAME = "snapshot";
  public static final String SEGMENTS_FILESET_NAME = "segments";
  public static final String METADATA_DIRECTORY_NAME = "metadata";
  public static final String METADATA_OBJECT_NAME = "metadata.json";

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
}
