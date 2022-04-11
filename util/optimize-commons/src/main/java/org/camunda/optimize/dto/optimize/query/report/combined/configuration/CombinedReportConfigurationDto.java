/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.combined.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;
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
  // needed to ensure the name is serialized properly, see https://stackoverflow.com/a/30207335
  @JsonProperty("yLabel")
  private String yLabel = "";
  // needed to ensure the name is serialized properly, see https://stackoverflow.com/a/30207335
  @JsonProperty("xLabel")
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
