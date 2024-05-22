/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.configuration.target_value;

import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class BaseLineDto {

  private TargetValueUnit unit = TargetValueUnit.HOURS;
  private String value = "0";

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof final BaseLineDto that)) {
      return false;
    }
    return unit == that.unit && Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(unit, value);
  }
}
