/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.management.dto;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Objects;

public class GetBackupStateResponseDetailDto {

  private String snapshotName;
  private String state;
  private OffsetDateTime startTime;
  private String[] failures;

  public GetBackupStateResponseDetailDto() {
  }

  public String getSnapshotName() {
    return snapshotName;
  }

  public GetBackupStateResponseDetailDto setSnapshotName(String snapshotName) {
    this.snapshotName = snapshotName;
    return this;
  }

  public String getState() {
    return state;
  }

  public GetBackupStateResponseDetailDto setState(String state) {
    this.state = state;
    return this;
  }

  public OffsetDateTime getStartTime() {
    return startTime;
  }

  public GetBackupStateResponseDetailDto setStartTime(OffsetDateTime startTime) {
    this.startTime = startTime;
    return this;
  }

  public String[] getFailures() {
    return failures;
  }

  public GetBackupStateResponseDetailDto setFailures(String[] failures) {
    this.failures = failures;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    GetBackupStateResponseDetailDto that = (GetBackupStateResponseDetailDto) o;
    return Objects.equals(snapshotName, that.snapshotName) && Objects.equals(state, that.state) && Objects.equals(
        startTime, that.startTime) && Arrays.equals(failures, that.failures);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(snapshotName, state, startTime);
    result = 31 * result + Arrays.hashCode(failures);
    return result;
  }
}
