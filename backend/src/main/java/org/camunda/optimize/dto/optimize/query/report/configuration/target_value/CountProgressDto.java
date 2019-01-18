package org.camunda.optimize.dto.optimize.query.report.configuration.target_value;

import java.util.Objects;

public class CountProgressDto {

  private Integer baseline = 0;
  private Integer target = 100;

  public Integer getBaseline() {
    return baseline;
  }

  public void setBaseline(Integer baseline) {
    this.baseline = baseline;
  }

  public Integer getTarget() {
    return target;
  }

  public void setTarget(Integer target) {
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
