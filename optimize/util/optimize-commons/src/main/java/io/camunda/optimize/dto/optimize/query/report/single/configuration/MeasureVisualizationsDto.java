/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.configuration;

import io.camunda.optimize.dto.optimize.ReportConstants;

public class MeasureVisualizationsDto {

  private String frequency = ReportConstants.BAR_VISUALIZATION;
  private String duration = ReportConstants.LINE_VISUALIZATION;

  public MeasureVisualizationsDto() {}

  public String getFrequency() {
    return frequency;
  }

  public void setFrequency(final String frequency) {
    this.frequency = frequency;
  }

  public String getDuration() {
    return duration;
  }

  public void setDuration(final String duration) {
    this.duration = duration;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof MeasureVisualizationsDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $frequency = getFrequency();
    result = result * PRIME + ($frequency == null ? 43 : $frequency.hashCode());
    final Object $duration = getDuration();
    result = result * PRIME + ($duration == null ? 43 : $duration.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof MeasureVisualizationsDto)) {
      return false;
    }
    final MeasureVisualizationsDto other = (MeasureVisualizationsDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$frequency = getFrequency();
    final Object other$frequency = other.getFrequency();
    if (this$frequency == null
        ? other$frequency != null
        : !this$frequency.equals(other$frequency)) {
      return false;
    }
    final Object this$duration = getDuration();
    final Object other$duration = other.getDuration();
    if (this$duration == null ? other$duration != null : !this$duration.equals(other$duration)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "MeasureVisualizationsDto(frequency="
        + getFrequency()
        + ", duration="
        + getDuration()
        + ")";
  }

  public static final class Fields {

    public static final String frequency = "frequency";
    public static final String duration = "duration";
  }
}
