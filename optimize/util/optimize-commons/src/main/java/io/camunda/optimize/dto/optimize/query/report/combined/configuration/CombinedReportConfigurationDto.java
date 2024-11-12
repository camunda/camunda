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
  public int hashCode() {
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  public String toString() {
    return "CombinedReportConfigurationDto(pointMarkers="
        + getPointMarkers()
        + ", hideRelativeValue="
        + getHideRelativeValue()
        + ", hideAbsoluteValue="
        + getHideAbsoluteValue()
        + ", yLabel="
        + getYLabel()
        + ", xLabel="
        + getXLabel()
        + ", alwaysShowRelative="
        + getAlwaysShowRelative()
        + ", alwaysShowAbsolute="
        + getAlwaysShowAbsolute()
        + ", targetValue="
        + getTargetValue()
        + ")";
  }

  public Boolean getPointMarkers() {
    return pointMarkers;
  }

  public void setPointMarkers(final Boolean pointMarkers) {
    this.pointMarkers = pointMarkers;
  }

  public Boolean getHideRelativeValue() {
    return hideRelativeValue;
  }

  public void setHideRelativeValue(final Boolean hideRelativeValue) {
    this.hideRelativeValue = hideRelativeValue;
  }

  public Boolean getHideAbsoluteValue() {
    return hideAbsoluteValue;
  }

  public void setHideAbsoluteValue(final Boolean hideAbsoluteValue) {
    this.hideAbsoluteValue = hideAbsoluteValue;
  }

  public String getYLabel() {
    return yLabel;
  }

  @JsonProperty("yLabel")
  public void setYLabel(final String yLabel) {
    this.yLabel = yLabel;
  }

  public String getXLabel() {
    return xLabel;
  }

  @JsonProperty("xLabel")
  public void setXLabel(final String xLabel) {
    this.xLabel = xLabel;
  }

  public Boolean getAlwaysShowRelative() {
    return alwaysShowRelative;
  }

  public void setAlwaysShowRelative(final Boolean alwaysShowRelative) {
    this.alwaysShowRelative = alwaysShowRelative;
  }

  public Boolean getAlwaysShowAbsolute() {
    return alwaysShowAbsolute;
  }

  public void setAlwaysShowAbsolute(final Boolean alwaysShowAbsolute) {
    this.alwaysShowAbsolute = alwaysShowAbsolute;
  }

  public CombinedReportTargetValueDto getTargetValue() {
    return targetValue;
  }

  public void setTargetValue(final CombinedReportTargetValueDto targetValue) {
    this.targetValue = targetValue;
  }
}
