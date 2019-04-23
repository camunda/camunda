/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.configuration.target_value;

import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

@Getter
@Setter
public class SingleReportTargetValueDto {

  private SingleReportCountChartDto countChart = new SingleReportCountChartDto();
  private DurationProgressDto durationProgress = new DurationProgressDto();
  private Boolean active = false;
  private CountProgressDto countProgress = new CountProgressDto();
  private SingleReportDurationChartDto durationChart = new SingleReportDurationChartDto();

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof SingleReportTargetValueDto)) {
      return false;
    }
    SingleReportTargetValueDto that = (SingleReportTargetValueDto) o;
    return Objects.equals(countChart, that.countChart) &&
      Objects.equals(durationProgress, that.durationProgress) &&
      Objects.equals(active, that.active) &&
      Objects.equals(countProgress, that.countProgress) &&
      Objects.equals(durationChart, that.durationChart);
  }

  @Override
  public int hashCode() {
    return Objects.hash(countChart, durationProgress, active, countProgress, durationChart);
  }
}
