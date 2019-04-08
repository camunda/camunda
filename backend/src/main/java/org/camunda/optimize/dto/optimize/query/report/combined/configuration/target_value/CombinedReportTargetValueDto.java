/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.combined.configuration.target_value;

import java.util.Objects;

public class CombinedReportTargetValueDto {

  private CombinedReportCountChartDto countChart = new CombinedReportCountChartDto();
  private Boolean active = false;
  private CombinedReportDurationChartDto durationChart = new CombinedReportDurationChartDto();

  public CombinedReportCountChartDto getCountChart() {
    return countChart;
  }

  public void setCountChart(CombinedReportCountChartDto countChart) {
    this.countChart = countChart;
  }

  public Boolean getActive() {
    return active;
  }

  public void setActive(Boolean active) {
    this.active = active;
  }

  public CombinedReportDurationChartDto getDurationChart() {
    return durationChart;
  }

  public void setDurationChart(CombinedReportDurationChartDto durationChart) {
    this.durationChart = durationChart;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof CombinedReportTargetValueDto)) {
      return false;
    }
    CombinedReportTargetValueDto that = (CombinedReportTargetValueDto) o;
    return Objects.equals(countChart, that.countChart) &&
      Objects.equals(active, that.active) &&
      Objects.equals(durationChart, that.durationChart);
  }

  @Override
  public int hashCode() {
    return Objects.hash(countChart, active, durationChart);
  }
}
