/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.es.backup;

import java.util.Objects;

public class Metadata {

  private static final String SNAPSHOT_NAME_PATTERN = "{prefix}{version}_part_{index}_of_{count}";
  private static final String SNAPSHOT_NAME_PREFIX = "camunda_tasklist_{backupId}_";

  private String version;
  private Integer partNo;
  private Integer partCount;

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

  public String buildSnapshotName(String backupId) {
    return SNAPSHOT_NAME_PATTERN
        .replace("{prefix}", buildSnapshotNamePrefix(backupId))
        .replace("{version}", version)
        .replace("{index}", partNo + "")
        .replace("{count}", partCount + "");
  }

  public static String buildSnapshotNamePrefix(String backupId) {
    return SNAPSHOT_NAME_PREFIX.replace("{backupId}", backupId);
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
