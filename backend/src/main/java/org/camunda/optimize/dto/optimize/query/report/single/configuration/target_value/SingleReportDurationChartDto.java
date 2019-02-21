package org.camunda.optimize.dto.optimize.query.report.single.configuration.target_value;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public class SingleReportDurationChartDto {

  private TargetValueUnit unit = TargetValueUnit.HOURS;
  private Boolean isBelow = false;
  private String value = "2";

  public TargetValueUnit getUnit() {
    return unit;
  }

  public void setUnit(TargetValueUnit unit) {
    this.unit = unit;
  }

  @JsonProperty(value="isBelow")
  public Boolean getBelow() {
    return isBelow;
  }

  public void setBelow(Boolean below) {
    isBelow = below;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof SingleReportDurationChartDto)) {
      return false;
    }
    SingleReportDurationChartDto that = (SingleReportDurationChartDto) o;
    return unit == that.unit &&
      Objects.equals(isBelow, that.isBelow) &&
      Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(unit, isBelow, value);
  }
}
