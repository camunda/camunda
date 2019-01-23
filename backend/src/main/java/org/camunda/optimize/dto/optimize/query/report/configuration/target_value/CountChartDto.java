package org.camunda.optimize.dto.optimize.query.report.configuration.target_value;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public class CountChartDto {

  private Boolean isBelow = false;
  private Double value = 100.0;

  @JsonProperty(value="isBelow")
  public Boolean getBelow() {
    return isBelow;
  }

  public void setBelow(Boolean below) {
    isBelow = below;
  }

  public Double getValue() {
    return value;
  }

  public void setValue(Double value) {
    this.value = value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof CountChartDto)) {
      return false;
    }
    CountChartDto that = (CountChartDto) o;
    return Objects.equals(isBelow, that.isBelow) &&
      Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(isBelow, value);
  }
}
