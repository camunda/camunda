/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.es.backup;

import io.camunda.operate.exceptions.OperateRuntimeException;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Metadata {

  public final static String SNAPSHOT_NAME_PREFIX = "camunda_operate_";
  private final static String SNAPSHOT_NAME_PATTERN = "{prefix}{version}_part_{index}_of_{count}";
  private final static String SNAPSHOT_NAME_PREFIX_PATTERN = SNAPSHOT_NAME_PREFIX + "{backupId}_";
  private final static Pattern BACKUPID_PATTERN = Pattern.compile(SNAPSHOT_NAME_PREFIX + "(\\d*)_.*");

  private Integer backupId;
  private String version;
  private Integer partNo;
  private Integer partCount;

  public Integer getBackupId() {
    return backupId;
  }

  public Metadata setBackupId(Integer backupId) {
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

  public static String buildSnapshotNamePrefix(Integer backupId) {
    return SNAPSHOT_NAME_PREFIX_PATTERN.replace("{backupId}", String.valueOf(backupId));
  }

  //backward compatibility with v. 8.1
  public static Integer extractBackupIdFromSnapshotName(String snapshotName) {
    Matcher matcher = BACKUPID_PATTERN.matcher(snapshotName);
    if (matcher.matches()) {
      return Integer.valueOf(matcher.group(1));
    } else {
      throw new OperateRuntimeException("Unable to extract backupId. Snapshot name: " + snapshotName);
    }
  }

  @Override public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    Metadata that = (Metadata) o;
    return Objects.equals(version, that.version) && Objects.equals(partNo, that.partNo) && Objects.equals(partCount,
        that.partCount);
  }

  @Override public int hashCode() {
    return Objects.hash(version, partNo, partCount);
  }
}
