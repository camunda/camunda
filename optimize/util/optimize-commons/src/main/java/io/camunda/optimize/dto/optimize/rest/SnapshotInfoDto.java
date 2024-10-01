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

public class SnapshotInfoDto {

  private String snapshotName;
  private SnapshotState state;
  private OffsetDateTime startTime;
  private List<String> failures;

  public SnapshotInfoDto(
      String snapshotName, SnapshotState state, OffsetDateTime startTime, List<String> failures) {
    this.snapshotName = snapshotName;
    this.state = state;
    this.startTime = startTime;
    this.failures = failures;
  }

  public SnapshotInfoDto() {}

  public String getSnapshotName() {
    return this.snapshotName;
  }

  public SnapshotState getState() {
    return this.state;
  }

  public OffsetDateTime getStartTime() {
    return this.startTime;
  }

  public List<String> getFailures() {
    return this.failures;
  }

  public void setSnapshotName(String snapshotName) {
    this.snapshotName = snapshotName;
  }

  public void setState(SnapshotState state) {
    this.state = state;
  }

  public void setStartTime(OffsetDateTime startTime) {
    this.startTime = startTime;
  }

  public void setFailures(List<String> failures) {
    this.failures = failures;
  }

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
    final Object this$snapshotName = this.getSnapshotName();
    final Object other$snapshotName = other.getSnapshotName();
    if (this$snapshotName == null
        ? other$snapshotName != null
        : !this$snapshotName.equals(other$snapshotName)) {
      return false;
    }
    final Object this$state = this.getState();
    final Object other$state = other.getState();
    if (this$state == null ? other$state != null : !this$state.equals(other$state)) {
      return false;
    }
    final Object this$startTime = this.getStartTime();
    final Object other$startTime = other.getStartTime();
    if (this$startTime == null
        ? other$startTime != null
        : !this$startTime.equals(other$startTime)) {
      return false;
    }
    final Object this$failures = this.getFailures();
    final Object other$failures = other.getFailures();
    if (this$failures == null ? other$failures != null : !this$failures.equals(other$failures)) {
      return false;
    }
    return true;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof SnapshotInfoDto;
  }

  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $snapshotName = this.getSnapshotName();
    result = result * PRIME + ($snapshotName == null ? 43 : $snapshotName.hashCode());
    final Object $state = this.getState();
    result = result * PRIME + ($state == null ? 43 : $state.hashCode());
    final Object $startTime = this.getStartTime();
    result = result * PRIME + ($startTime == null ? 43 : $startTime.hashCode());
    final Object $failures = this.getFailures();
    result = result * PRIME + ($failures == null ? 43 : $failures.hashCode());
    return result;
  }

  public String toString() {
    return "SnapshotInfoDto(snapshotName="
        + this.getSnapshotName()
        + ", state="
        + this.getState()
        + ", startTime="
        + this.getStartTime()
        + ", failures="
        + this.getFailures()
        + ")";
  }
}
