/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.combined.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.optimize.dto.optimize.query.report.combined.configuration.target_value.CombinedReportTargetValueDto;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
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
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof final CombinedReportConfigurationDto that)) {
      return false;
    }
    return Objects.equals(pointMarkers, that.pointMarkers)
        && Objects.equals(hideRelativeValue, that.hideRelativeValue)
        && Objects.equals(hideAbsoluteValue, that.hideAbsoluteValue)
        && Objects.equals(yLabel, that.yLabel)
        && Objects.equals(xLabel, that.xLabel)
        && Objects.equals(alwaysShowRelative, that.alwaysShowRelative)
        && Objects.equals(alwaysShowAbsolute, that.alwaysShowAbsolute)
        && Objects.equals(targetValue, that.targetValue);
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
        targetValue);
  }
}
