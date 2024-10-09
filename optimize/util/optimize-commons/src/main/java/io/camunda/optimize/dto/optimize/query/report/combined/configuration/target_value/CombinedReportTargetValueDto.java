/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.combined.configuration.target_value;

import java.util.Objects;

public class CombinedReportTargetValueDto {

  private CombinedReportCountChartDto countChart = new CombinedReportCountChartDto();
  private Boolean active = false;
  private CombinedReportDurationChartDto durationChart = new CombinedReportDurationChartDto();

  public CombinedReportTargetValueDto(
      final CombinedReportCountChartDto countChart,
      final Boolean active,
      final CombinedReportDurationChartDto durationChart) {
    this.countChart = countChart;
    this.active = active;
    this.durationChart = durationChart;
  }

  public CombinedReportTargetValueDto() {}

  @Override
  public int hashCode() {
    return Objects.hash(countChart, active, durationChart);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof final CombinedReportTargetValueDto that)) {
      return false;
    }
    return Objects.equals(countChart, that.countChart)
        && Objects.equals(active, that.active)
        && Objects.equals(durationChart, that.durationChart);
  }

  @Override
  public String toString() {
    return "CombinedReportTargetValueDto(countChart="
        + getCountChart()
        + ", active="
        + getActive()
        + ", durationChart="
        + getDurationChart()
        + ")";
  }

  public CombinedReportCountChartDto getCountChart() {
    return countChart;
  }

  public void setCountChart(final CombinedReportCountChartDto countChart) {
    this.countChart = countChart;
  }

  public Boolean getActive() {
    return active;
  }

  public void setActive(final Boolean active) {
    this.active = active;
  }

  public CombinedReportDurationChartDto getDurationChart() {
    return durationChart;
  }

  public void setDurationChart(final CombinedReportDurationChartDto durationChart) {
    this.durationChart = durationChart;
  }
}
