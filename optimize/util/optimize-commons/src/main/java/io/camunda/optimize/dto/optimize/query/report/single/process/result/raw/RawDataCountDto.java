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
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
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
