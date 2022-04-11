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
public class SingleReportDurationChartDto {

  private TargetValueUnit unit = TargetValueUnit.HOURS;
  private Boolean isBelow = false;
  private String value = "2";

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
