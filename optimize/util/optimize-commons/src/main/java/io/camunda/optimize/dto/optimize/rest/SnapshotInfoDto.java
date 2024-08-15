/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest;

import java.time.OffsetDateTime;
import java.util.List;
import org.elasticsearch.snapshots.SnapshotState;

public class SnapshotInfoDto {

  private String snapshotName;
  private SnapshotState state;
  private OffsetDateTime startTime;
  private List<String> failures;

  public SnapshotInfoDto(
      final String snapshotName,
      final SnapshotState state,
      final OffsetDateTime startTime,
      final List<String> failures) {
    this.snapshotName = snapshotName;
    this.state = state;
    this.startTime = startTime;
    this.failures = failures;
  }

  public SnapshotInfoDto() {}

  public String getSnapshotName() {
    return snapshotName;
  }

  public void setSnapshotName(final String snapshotName) {
    this.snapshotName = snapshotName;
  }

  public SnapshotState getState() {
    return state;
  }

  public void setState(final SnapshotState state) {
    this.state = state;
  }

  public OffsetDateTime getStartTime() {
    return startTime;
  }

  public void setStartTime(final OffsetDateTime startTime) {
    this.startTime = startTime;
  }

  public List<String> getFailures() {
    return failures;
  }

  public void setFailures(final List<String> failures) {
    this.failures = failures;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof SnapshotInfoDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $snapshotName = getSnapshotName();
    result = result * PRIME + ($snapshotName == null ? 43 : $snapshotName.hashCode());
    final Object $state = getState();
    result = result * PRIME + ($state == null ? 43 : $state.hashCode());
    final Object $startTime = getStartTime();
    result = result * PRIME + ($startTime == null ? 43 : $startTime.hashCode());
    final Object $failures = getFailures();
    result = result * PRIME + ($failures == null ? 43 : $failures.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof SnapshotInfoDto)) {
      return false;
    }
    final SnapshotInfoDto other = (SnapshotInfoDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$snapshotName = getSnapshotName();
    final Object other$snapshotName = other.getSnapshotName();
    if (this$snapshotName == null
        ? other$snapshotName != null
        : !this$snapshotName.equals(other$snapshotName)) {
      return false;
    }
    final Object this$state = getState();
    final Object other$state = other.getState();
    if (this$state == null ? other$state != null : !this$state.equals(other$state)) {
      return false;
    }
    final Object this$startTime = getStartTime();
    final Object other$startTime = other.getStartTime();
    if (this$startTime == null
        ? other$startTime != null
        : !this$startTime.equals(other$startTime)) {
      return false;
    }
    final Object this$failures = getFailures();
    final Object other$failures = other.getFailures();
    if (this$failures == null ? other$failures != null : !this$failures.equals(other$failures)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "SnapshotInfoDto(snapshotName="
        + getSnapshotName()
        + ", state="
        + getState()
        + ", startTime="
        + getStartTime()
        + ", failures="
        + getFailures()
        + ")";
  }
}
