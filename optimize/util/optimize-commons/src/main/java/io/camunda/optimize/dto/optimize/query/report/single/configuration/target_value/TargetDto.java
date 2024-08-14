/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.configuration.target_value;

import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class TargetDto {

  private TargetValueUnit unit = TargetValueUnit.HOURS;
  private String value = "2";
  private Boolean isBelow = false;

  @Override
  public int hashCode() {
    return Objects.hash(unit, value);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof final TargetDto targetDto)) {
      return false;
    }
    return unit == targetDto.unit
        && Objects.equals(isBelow, targetDto.isBelow)
        && Objects.equals(value, targetDto.value);
  }

  public static final class Fields {

    public static final String unit = "unit";
    public static final String value = "value";
    public static final String isBelow = "isBelow";
  }
}
