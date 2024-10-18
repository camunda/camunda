/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.configuration.target_value;

public class SingleReportTargetValueDto {

  private SingleReportCountChartDto countChart = new SingleReportCountChartDto();
  private DurationProgressDto durationProgress = new DurationProgressDto();
  private Boolean active = false;
  private CountProgressDto countProgress = new CountProgressDto();
  private SingleReportDurationChartDto durationChart = new SingleReportDurationChartDto();
  private Boolean isKpi;

  public SingleReportTargetValueDto() {}

  public SingleReportCountChartDto getCountChart() {
    return countChart;
  }

  public void setCountChart(final SingleReportCountChartDto countChart) {
    this.countChart = countChart;
  }

  public DurationProgressDto getDurationProgress() {
    return durationProgress;
  }

  public void setDurationProgress(final DurationProgressDto durationProgress) {
    this.durationProgress = durationProgress;
  }

  public Boolean getActive() {
    return active;
  }

  public void setActive(final Boolean active) {
    this.active = active;
  }

  public CountProgressDto getCountProgress() {
    return countProgress;
  }

  public void setCountProgress(final CountProgressDto countProgress) {
    this.countProgress = countProgress;
  }

  public SingleReportDurationChartDto getDurationChart() {
    return durationChart;
  }

  public void setDurationChart(final SingleReportDurationChartDto durationChart) {
    this.durationChart = durationChart;
  }

  public Boolean getIsKpi() {
    return isKpi;
  }

  public void setIsKpi(final Boolean isKpi) {
    this.isKpi = isKpi;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof SingleReportTargetValueDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $countChart = getCountChart();
    result = result * PRIME + ($countChart == null ? 43 : $countChart.hashCode());
    final Object $durationProgress = getDurationProgress();
    result = result * PRIME + ($durationProgress == null ? 43 : $durationProgress.hashCode());
    final Object $active = getActive();
    result = result * PRIME + ($active == null ? 43 : $active.hashCode());
    final Object $countProgress = getCountProgress();
    result = result * PRIME + ($countProgress == null ? 43 : $countProgress.hashCode());
    final Object $durationChart = getDurationChart();
    result = result * PRIME + ($durationChart == null ? 43 : $durationChart.hashCode());
    final Object $isKpi = getIsKpi();
    result = result * PRIME + ($isKpi == null ? 43 : $isKpi.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof SingleReportTargetValueDto)) {
      return false;
    }
    final SingleReportTargetValueDto other = (SingleReportTargetValueDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$countChart = getCountChart();
    final Object other$countChart = other.getCountChart();
    if (this$countChart == null
        ? other$countChart != null
        : !this$countChart.equals(other$countChart)) {
      return false;
    }
    final Object this$durationProgress = getDurationProgress();
    final Object other$durationProgress = other.getDurationProgress();
    if (this$durationProgress == null
        ? other$durationProgress != null
        : !this$durationProgress.equals(other$durationProgress)) {
      return false;
    }
    final Object this$active = getActive();
    final Object other$active = other.getActive();
    if (this$active == null ? other$active != null : !this$active.equals(other$active)) {
      return false;
    }
    final Object this$countProgress = getCountProgress();
    final Object other$countProgress = other.getCountProgress();
    if (this$countProgress == null
        ? other$countProgress != null
        : !this$countProgress.equals(other$countProgress)) {
      return false;
    }
    final Object this$durationChart = getDurationChart();
    final Object other$durationChart = other.getDurationChart();
    if (this$durationChart == null
        ? other$durationChart != null
        : !this$durationChart.equals(other$durationChart)) {
      return false;
    }
    final Object this$isKpi = getIsKpi();
    final Object other$isKpi = other.getIsKpi();
    if (this$isKpi == null ? other$isKpi != null : !this$isKpi.equals(other$isKpi)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "SingleReportTargetValueDto(countChart="
        + getCountChart()
        + ", durationProgress="
        + getDurationProgress()
        + ", active="
        + getActive()
        + ", countProgress="
        + getCountProgress()
        + ", durationChart="
        + getDurationChart()
        + ", isKpi="
        + getIsKpi()
        + ")";
  }

  public static final class Fields {

    public static final String countChart = "countChart";
    public static final String durationProgress = "durationProgress";
    public static final String active = "active";
    public static final String countProgress = "countProgress";
    public static final String durationChart = "durationChart";
    public static final String isKpi = "isKpi";
  }
}
