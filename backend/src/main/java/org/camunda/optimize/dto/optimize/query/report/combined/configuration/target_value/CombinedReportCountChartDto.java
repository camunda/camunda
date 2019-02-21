package org.camunda.optimize.dto.optimize.query.report.combined.configuration.target_value;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public class CombinedReportCountChartDto {

  private Boolean isBelow = false;
  private String value = "100";

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
    if (!(o instanceof CombinedReportCountChartDto)) {
      return false;
    }
    CombinedReportCountChartDto that = (CombinedReportCountChartDto) o;
    return Objects.equals(isBelow, that.isBelow) &&
      Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(isBelow, value);
  }
}
