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
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
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

  @SuppressWarnings("checkstyle:ConstantName")
  public static final class Fields {

    public static final String countChart = "countChart";
    public static final String durationProgress = "durationProgress";
    public static final String active = "active";
    public static final String countProgress = "countProgress";
    public static final String durationChart = "durationChart";
    public static final String isKpi = "isKpi";
  }
}
