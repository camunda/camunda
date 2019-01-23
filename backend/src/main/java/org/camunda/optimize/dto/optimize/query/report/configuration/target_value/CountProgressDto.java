package org.camunda.optimize.dto.optimize.query.report.configuration.target_value;

import java.util.Objects;

public class CountProgressDto {

  private Double baseline = 0.0;
  private Double target = 100.0;

  public Double getBaseline() {
    return baseline;
  }

  public void setBaseline(Double baseline) {
    this.baseline = baseline;
  }

  public Double getTarget() {
    return target;
  }

  public void setTarget(Double target) {
    this.target = target;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof CountProgressDto)) {
      return false;
    }
    CountProgressDto that = (CountProgressDto) o;
    return Objects.equals(baseline, that.baseline) &&
      Objects.equals(target, that.target);
  }

  @Override
  public int hashCode() {
    return Objects.hash(baseline, target);
  }
}
