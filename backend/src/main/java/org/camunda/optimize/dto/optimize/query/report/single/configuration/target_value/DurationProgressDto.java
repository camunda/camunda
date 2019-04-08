/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.configuration.target_value;

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
