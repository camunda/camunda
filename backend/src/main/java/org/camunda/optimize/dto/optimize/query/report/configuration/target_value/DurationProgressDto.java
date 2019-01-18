package org.camunda.optimize.dto.optimize.query.report.configuration.target_value;

import java.util.Objects;

public class DurationProgressDto {

  private BaseLineDto baseline = new BaseLineDto();
  private TargetDto target = new TargetDto();

  public BaseLineDto getBaseline() {
    return baseline;
  }

  public void setBaseline(BaseLineDto baseline) {
    this.baseline = baseline;
  }

  public TargetDto getTarget() {
    return target;
  }

  public void setTarget(TargetDto target) {
    this.target = target;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof DurationProgressDto)) {
      return false;
    }
    DurationProgressDto that = (DurationProgressDto) o;
    return Objects.equals(baseline, that.baseline) &&
      Objects.equals(target, that.target);
  }

  @Override
  public int hashCode() {
    return Objects.hash(baseline, target);
  }
}
