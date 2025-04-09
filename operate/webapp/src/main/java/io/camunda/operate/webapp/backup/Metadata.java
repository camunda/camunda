/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.backup;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.exceptions.OperateRuntimeException;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Metadata {

  public static final String SNAPSHOT_NAME_PREFIX = "camunda_operate_";
  private static final String SNAPSHOT_NAME_PATTERN = "{prefix}{version}_part_{index}_of_{count}";
  private static final String SNAPSHOT_NAME_PREFIX_PATTERN = SNAPSHOT_NAME_PREFIX + "{backupId}_";
  private static final Pattern BACKUPID_PATTERN =
      Pattern.compile(SNAPSHOT_NAME_PREFIX + "(\\d*)_.*");
  private static final Pattern METADATA_PATTERN =
      Pattern.compile(
          SNAPSHOT_NAME_PREFIX
              + "(?<backupId>\\d+)_(?<version>[^_]+)_part_(?<index>\\d+)_of_(?<count>\\d+)");

  private Long backupId;
  private String version;
  private Integer partNo;
  private Integer partCount;

  public Metadata() {}

  public boolean isInitialized() {
    return partCount != null && partNo != null && version != null;
  }

  public static String buildSnapshotNamePrefix(final Long backupId) {
    return SNAPSHOT_NAME_PREFIX_PATTERN.replace("{backupId}", String.valueOf(backupId));
  }

  // backward compatibility with v. 8.1
  public static Long extractBackupIdFromSnapshotName(final String snapshotName) {
    final Matcher matcher = BACKUPID_PATTERN.matcher(snapshotName);
    if (matcher.matches()) {
      return Long.valueOf(matcher.group(1));
    } else {
      throw new OperateRuntimeException(
          "Unable to extract backupId. Snapshot name: " + snapshotName);
    }
  }

  public static Metadata extractMetadataFromSnapshotName(final String snapshotName) {
    Objects.requireNonNull(snapshotName, "Snapshot name cannot be null");
    final Matcher matcher = METADATA_PATTERN.matcher(snapshotName);
    if (matcher.matches()) {
      final Long backupId = Long.parseLong(matcher.group("backupId"));
      final String version = matcher.group("version");
      final Integer index = Integer.parseInt(matcher.group("index"));
      final Integer count = Integer.parseInt(matcher.group("count"));

      return new Metadata()
          .setBackupId(backupId)
          .setPartCount(count)
          .setPartNo(index)
          .setVersion(version);
    } else {
      throw new IllegalArgumentException(
          "Unable to extract metadata. Snapshot name: " + snapshotName);
    }
  }

  public static Metadata extractFromMetadataOrName(
      final ObjectMapper objectMapper,
      final Map<String, Object> metadata,
      final String snapshotName) {
    final Metadata fromMetadata = objectMapper.convertValue(metadata, Metadata.class);
    if (fromMetadata != null && fromMetadata.isInitialized()) {
      return fromMetadata;
    } else {
      return Metadata.extractMetadataFromSnapshotName(snapshotName);
    }
  }

  public Long getBackupId() {
    return backupId;
  }

  public Metadata setBackupId(final Long backupId) {
    this.backupId = backupId;
    return this;
  }

  public String getVersion() {
    return version;
  }

  public Metadata setVersion(final String version) {
    this.version = version;
    return this;
  }

  public Integer getPartNo() {
    return partNo;
  }

  public Metadata setPartNo(final Integer partNo) {
    this.partNo = partNo;
    return this;
  }

  public Integer getPartCount() {
    return partCount;
  }

  public Metadata setPartCount(final Integer partCount) {
    this.partCount = partCount;
    return this;
  }

  public String buildSnapshotName() {
    return SNAPSHOT_NAME_PATTERN
        .replace("{prefix}", buildSnapshotNamePrefix(backupId))
        .replace("{version}", version)
        .replace("{index}", partNo + "")
        .replace("{count}", partCount + "");
  }

  @Override
  public int hashCode() {
    return Objects.hash(version, partNo, partCount);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final Metadata that = (Metadata) o;
    return Objects.equals(version, that.version)
        && Objects.equals(partNo, that.partNo)
        && Objects.equals(partCount, that.partCount);
  }
}
