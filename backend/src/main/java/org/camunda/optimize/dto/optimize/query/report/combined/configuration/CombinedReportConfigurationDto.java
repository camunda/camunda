/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.combined.configuration;

import lombok.Getter;
import lombok.Setter;
import org.camunda.optimize.dto.optimize.query.report.combined.configuration.target_value.CombinedReportTargetValueDto;

import java.util.Objects;

@Getter
@Setter
public class CombinedReportConfigurationDto {

  private Boolean pointMarkers = true;
  private Boolean hideRelativeValue = false;
  private Boolean hideAbsoluteValue = false;
  private String yLabel = "";
  private String xLabel = "";
  private Boolean alwaysShowRelative = false;
  private Boolean alwaysShowAbsolute = false;
  private CombinedReportTargetValueDto targetValue = new CombinedReportTargetValueDto();

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof CombinedReportConfigurationDto)) {
      return false;
    }
    CombinedReportConfigurationDto that = (CombinedReportConfigurationDto) o;
    return
      Objects.equals(pointMarkers, that.pointMarkers) &&
        Objects.equals(hideRelativeValue, that.hideRelativeValue) &&
        Objects.equals(hideAbsoluteValue, that.hideAbsoluteValue) &&
        Objects.equals(yLabel, that.yLabel) &&
        Objects.equals(xLabel, that.xLabel) &&
        Objects.equals(alwaysShowRelative, that.alwaysShowRelative) &&
        Objects.equals(alwaysShowAbsolute, that.alwaysShowAbsolute) &&
        Objects.equals(targetValue, that.targetValue);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
      pointMarkers,
      hideRelativeValue,
      hideAbsoluteValue,
      yLabel,
      xLabel,
      alwaysShowRelative,
      alwaysShowAbsolute,
      targetValue
    );
  }
}
