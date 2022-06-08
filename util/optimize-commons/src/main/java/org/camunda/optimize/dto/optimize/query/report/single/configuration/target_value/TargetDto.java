/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.configuration.target_value;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;

import java.util.Objects;

@Getter
@Setter
@FieldNameConstants
public class TargetDto {

  private TargetValueUnit unit = TargetValueUnit.HOURS;
  private String value = "2";
  private Boolean isBelow = false;

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
      Objects.equals(isBelow, targetDto.isBelow) &&
      Objects.equals(value, targetDto.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(unit, value);
  }
}
