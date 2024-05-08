/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.es.backup;

import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Metadata {

  public static final String SNAPSHOT_NAME_PREFIX = "camunda_tasklist_";
  private static final String SNAPSHOT_NAME_PATTERN = "{prefix}{version}_part_{index}_of_{count}";
  private static final String SNAPSHOT_NAME_PREFIX_PATTERN = SNAPSHOT_NAME_PREFIX + "{backupId}_";
  private static final Pattern BACKUPID_PATTERN =
      Pattern.compile(SNAPSHOT_NAME_PREFIX + "(\\d*)_.*");
  private Long backupId;
  private String version;
  private Integer partNo;
  private Integer partCount;

  public Long getBackupId() {
    return backupId;
  }

  public Metadata setBackupId(Long backupId) {
    this.backupId = backupId;
    return this;
  }

  public String getVersion() {
    return version;
  }

  public Metadata setVersion(String version) {
    this.version = version;
    return this;
  }

  public Integer getPartNo() {
    return partNo;
  }

  public Metadata setPartNo(Integer partNo) {
    this.partNo = partNo;
    return this;
  }

  public Integer getPartCount() {
    return partCount;
  }

  public Metadata setPartCount(Integer partCount) {
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

  public static String buildSnapshotNamePrefix(Long backupId) {
    return SNAPSHOT_NAME_PREFIX_PATTERN.replace("{backupId}", String.valueOf(backupId));
  }

  // backward compatibility with v. 8.1
  public static Long extractBackupIdFromSnapshotName(String snapshotName) {
    final Matcher matcher = BACKUPID_PATTERN.matcher(snapshotName);
    if (matcher.matches()) {
      return Long.valueOf(matcher.group(1));
    } else {
      throw new TasklistRuntimeException(
          "Unable to extract backupId. Snapshot name: " + snapshotName);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final Metadata metadata = (Metadata) o;
    return Objects.equals(version, metadata.version)
        && Objects.equals(partNo, metadata.partNo)
        && Objects.equals(partCount, metadata.partCount);
  }

  @Override
  public int hashCode() {
    return Objects.hash(version, partNo, partCount);
  }
}
