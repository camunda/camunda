/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.combined.configuration.target_value;

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
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
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
