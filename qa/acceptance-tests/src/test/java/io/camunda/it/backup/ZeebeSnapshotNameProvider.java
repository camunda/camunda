/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.backup;

import io.camunda.webapps.backup.BackupException;
import io.camunda.webapps.backup.Metadata;
import io.camunda.webapps.backup.repository.SnapshotNameProvider;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ZeebeSnapshotNameProvider implements SnapshotNameProvider {
  public static final String SNAPSHOT_NAME_PREFIX = "camunda_zeebe_";
  private static final String SNAPSHOT_NAME_PATTERN = "{prefix}{version}_part_{index}_of_{count}";
  private static final String SNAPSHOT_NAME_PREFIX_PATTERN = SNAPSHOT_NAME_PREFIX + "{backupId}_";
  private static final Pattern BACKUPID_PATTERN =
      Pattern.compile(SNAPSHOT_NAME_PREFIX + "(\\d*)_.*");
  private static final Pattern METADATA_PATTERN =
      Pattern.compile(
          SNAPSHOT_NAME_PREFIX
              + "(?<backupId>\\d+)_(?<version>[^_]+)_part_(?<index>\\d+)_of_(?<count>\\d+)");

  @Override
  public String getSnapshotNamePrefix(final long backupId) {
    return SNAPSHOT_NAME_PREFIX_PATTERN.replace("{backupId}", String.valueOf(backupId));
  }

  @Override
  public String getSnapshotName(final Metadata metadata) {
    return SNAPSHOT_NAME_PATTERN
        .replace("{prefix}", getSnapshotNamePrefix(metadata.backupId()))
        .replace("{version}", metadata.version())
        .replace("{index}", metadata.partNo() + "")
        .replace("{count}", metadata.partCount() + "");
  }

  @Override
  public Long extractBackupId(final String snapshotName) {
    final Matcher matcher = BACKUPID_PATTERN.matcher(snapshotName);
    if (matcher.matches()) {
      return Long.valueOf(matcher.group(1));
    } else {
      throw new BackupException("Unable to extract backupId. Snapshot name: " + snapshotName);
    }
  }

  @Override
  public Metadata extractMetadataFromSnapshotName(final String snapshotName) {
    final Matcher matcher = METADATA_PATTERN.matcher(snapshotName);
    if (matcher.matches()) {
      final Long backupId = Long.parseLong(matcher.group("backupId"));
      final String version = matcher.group("version");
      final Integer index = Integer.parseInt(matcher.group("index"));
      final Integer count = Integer.parseInt(matcher.group("count"));

      return new Metadata(backupId, version, index, count);
    } else {
      throw new IllegalArgumentException(
          "Unable to extract metadata. Snapshot name: " + snapshotName);
    }
  }

  @Override
  public String snapshotNamePrefix() {
    return SNAPSHOT_NAME_PREFIX;
  }
}
