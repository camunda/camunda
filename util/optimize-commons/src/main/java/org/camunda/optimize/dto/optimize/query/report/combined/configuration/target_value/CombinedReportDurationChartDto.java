/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.combined.configuration.target_value;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.target_value.TargetValueUnit;

import java.util.Objects;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CombinedReportDurationChartDto {

  private TargetValueUnit unit = TargetValueUnit.HOURS;
  private Boolean isBelow = false;
  private String value = "2";

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof CombinedReportDurationChartDto)) {
      return false;
    }
    CombinedReportDurationChartDto that = (CombinedReportDurationChartDto) o;
    return unit == that.unit &&
      Objects.equals(isBelow, that.isBelow) &&
      Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(unit, isBelow, value);
  }
}
