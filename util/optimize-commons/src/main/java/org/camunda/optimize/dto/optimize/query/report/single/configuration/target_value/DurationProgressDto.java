/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.configuration.target_value;

import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

@Getter
@Setter
public class DurationProgressDto {

  private BaseLineDto baseline = new BaseLineDto();
  private TargetDto target = new TargetDto();

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
