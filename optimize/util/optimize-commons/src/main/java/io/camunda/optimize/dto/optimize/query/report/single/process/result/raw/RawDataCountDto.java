/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.process.result.raw;

import io.camunda.optimize.dto.optimize.query.report.single.RawDataInstanceDto;

public class RawDataCountDto implements RawDataInstanceDto {

  protected long incidents;
  protected long openIncidents;
  protected long userTasks;

  public RawDataCountDto(final long incidents, final long openIncidents, final long userTasks) {
    this.incidents = incidents;
    this.openIncidents = openIncidents;
    this.userTasks = userTasks;
  }

  public RawDataCountDto() {}

  public long getIncidents() {
    return incidents;
  }

  public void setIncidents(final long incidents) {
    this.incidents = incidents;
  }

  public long getOpenIncidents() {
    return openIncidents;
  }

  public void setOpenIncidents(final long openIncidents) {
    this.openIncidents = openIncidents;
  }

  public long getUserTasks() {
    return userTasks;
  }

  public void setUserTasks(final long userTasks) {
    this.userTasks = userTasks;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof RawDataCountDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final long $incidents = getIncidents();
    result = result * PRIME + (int) ($incidents >>> 32 ^ $incidents);
    final long $openIncidents = getOpenIncidents();
    result = result * PRIME + (int) ($openIncidents >>> 32 ^ $openIncidents);
    final long $userTasks = getUserTasks();
    result = result * PRIME + (int) ($userTasks >>> 32 ^ $userTasks);
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof RawDataCountDto)) {
      return false;
    }
    final RawDataCountDto other = (RawDataCountDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (getIncidents() != other.getIncidents()) {
      return false;
    }
    if (getOpenIncidents() != other.getOpenIncidents()) {
      return false;
    }
    if (getUserTasks() != other.getUserTasks()) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "RawDataCountDto(incidents="
        + getIncidents()
        + ", openIncidents="
        + getOpenIncidents()
        + ", userTasks="
        + getUserTasks()
        + ")";
  }

  public enum Fields {
    incidents,
    openIncidents,
    userTasks
  }
}
