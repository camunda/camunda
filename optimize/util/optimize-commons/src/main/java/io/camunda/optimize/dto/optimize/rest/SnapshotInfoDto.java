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
import java.util.Objects;

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

  public void setSnapshotName(final String snapshotName) {
    this.snapshotName = snapshotName;
  }

  public void setState(final SnapshotState state) {
    this.state = state;
  }

  public void setStartTime(final OffsetDateTime startTime) {
    this.startTime = startTime;
  }

  public void setFailures(final List<String> failures) {
    this.failures = failures;
  }

  @Override
  public int hashCode() {
    return Objects.hash(snapshotName, state, failures);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final SnapshotInfoDto that = (SnapshotInfoDto) o;
    return Objects.equals(snapshotName, that.snapshotName)
        && Objects.equals(state, that.state)
        && Objects.equals(failures, that.failures);
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
