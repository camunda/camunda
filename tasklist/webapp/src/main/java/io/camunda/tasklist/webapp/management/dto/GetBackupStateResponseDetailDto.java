/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.management.dto;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Objects;

public class GetBackupStateResponseDetailDto {

  private String snapshotName;
  private String state;
  private OffsetDateTime startTime;
  private String[] failures;

  public GetBackupStateResponseDetailDto() {}

  public String getSnapshotName() {
    return snapshotName;
  }

  public GetBackupStateResponseDetailDto setSnapshotName(final String snapshotName) {
    this.snapshotName = snapshotName;
    return this;
  }

  public String getState() {
    return state;
  }

  public GetBackupStateResponseDetailDto setState(final String state) {
    this.state = state;
    return this;
  }

  public OffsetDateTime getStartTime() {
    return startTime;
  }

  public GetBackupStateResponseDetailDto setStartTime(final OffsetDateTime startTime) {
    this.startTime = startTime;
    return this;
  }

  public String[] getFailures() {
    return failures;
  }

  public GetBackupStateResponseDetailDto setFailures(final String[] failures) {
    this.failures = failures;
    return this;
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(snapshotName, state, startTime);
    result = 31 * result + Arrays.hashCode(failures);
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final GetBackupStateResponseDetailDto that = (GetBackupStateResponseDetailDto) o;
    return Objects.equals(snapshotName, that.snapshotName)
        && Objects.equals(state, that.state)
        && Objects.equals(startTime, that.startTime)
        && Arrays.equals(failures, that.failures);
  }

  @Override
  public String toString() {
    return "GetBackupStateResponseDetailDto{"
        + "snapshotName='"
        + snapshotName
        + '\''
        + ", state='"
        + state
        + '\''
        + ", startTime="
        + startTime
        + ", failures="
        + Arrays.toString(failures)
        + '}';
  }
}
