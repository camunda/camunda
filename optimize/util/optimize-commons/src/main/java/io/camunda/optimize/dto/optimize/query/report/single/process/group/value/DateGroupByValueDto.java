/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.process.group.value;

import io.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import java.util.Objects;

public class DateGroupByValueDto implements ProcessGroupByValueDto {

  protected AggregateByDateUnit unit;

  public DateGroupByValueDto(final AggregateByDateUnit unit) {
    this.unit = unit;
  }

  public DateGroupByValueDto() {}

  @Override
  public boolean isCombinable(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof DateGroupByValueDto)) {
      return false;
    }
    final DateGroupByValueDto that = (DateGroupByValueDto) o;
    return Objects.equals(unit, that.unit);
  }

  public AggregateByDateUnit getUnit() {
    return unit;
  }

  public void setUnit(final AggregateByDateUnit unit) {
    this.unit = unit;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof DateGroupByValueDto;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DateGroupByValueDto that = (DateGroupByValueDto) o;
    return unit == that.unit;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(unit);
  }

  @Override
  public String toString() {
    return "DateGroupByValueDto(unit=" + getUnit() + ")";
  }
}
