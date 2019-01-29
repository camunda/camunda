package org.camunda.optimize.dto.optimize.query.report.configuration.target_value;

import java.util.Objects;

public class TargetDto {

  private TargetValueUnit unit = TargetValueUnit.HOURS;
  private String value = "2";

  public TargetValueUnit getUnit() {
    return unit;
  }

  public void setUnit(TargetValueUnit unit) {
    this.unit = unit;
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
    if (!(o instanceof TargetDto)) {
      return false;
    }
    TargetDto targetDto = (TargetDto) o;
    return unit == targetDto.unit &&
      Objects.equals(value, targetDto.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(unit, value);
  }
}
