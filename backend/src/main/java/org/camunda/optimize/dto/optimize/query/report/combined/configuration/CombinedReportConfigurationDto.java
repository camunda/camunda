package org.camunda.optimize.dto.optimize.query.report.combined.configuration;

import org.camunda.optimize.dto.optimize.query.report.combined.configuration.target_value.CombinedReportTargetValueDto;

import java.util.Objects;

public class CombinedReportConfigurationDto {

  private Boolean pointMarkers = true;
  private Boolean hideRelativeValue = false;
  private Boolean hideAbsoluteValue = false;
  private String yLabel = "";
  private String xLabel = "";
  private CombinedReportTargetValueDto targetValue = new CombinedReportTargetValueDto();


  public Boolean getPointMarkers() {
    return pointMarkers;
  }

  public void setPointMarkers(Boolean pointMarkers) {
    this.pointMarkers = pointMarkers;
  }

  public Boolean getHideRelativeValue() {
    return hideRelativeValue;
  }

  public void setHideRelativeValue(Boolean hideRelativeValue) {
    this.hideRelativeValue = hideRelativeValue;
  }

  public Boolean getHideAbsoluteValue() {
    return hideAbsoluteValue;
  }

  public void setHideAbsoluteValue(Boolean hideAbsoluteValue) {
    this.hideAbsoluteValue = hideAbsoluteValue;
  }

  public String getyLabel() {
    return yLabel;
  }

  public void setyLabel(String yLabel) {
    this.yLabel = yLabel;
  }

  public String getxLabel() {
    return xLabel;
  }

  public void setxLabel(String xLabel) {
    this.xLabel = xLabel;
  }

  public CombinedReportTargetValueDto getTargetValue() {
    return targetValue;
  }

  public void setTargetValue(CombinedReportTargetValueDto targetValue) {
    this.targetValue = targetValue;
  }

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
      targetValue
    );
  }
}
