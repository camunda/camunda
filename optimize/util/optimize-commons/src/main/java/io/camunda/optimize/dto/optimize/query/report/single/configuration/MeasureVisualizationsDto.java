/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.configuration;

import io.camunda.optimize.dto.optimize.ReportConstants;
import java.util.Objects;

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
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final MeasureVisualizationsDto that = (MeasureVisualizationsDto) o;
    return Objects.equals(frequency, that.frequency) && Objects.equals(duration, that.duration);
  }

  @Override
  public int hashCode() {
    return Objects.hash(frequency, duration);
  }

  @Override
  public String toString() {
    return "MeasureVisualizationsDto(frequency="
        + getFrequency()
        + ", duration="
        + getDuration()
        + ")";
  }

  @SuppressWarnings("checkstyle:ConstantName")
  public static final class Fields {

    public static final String frequency = "frequency";
    public static final String duration = "duration";
  }
}
