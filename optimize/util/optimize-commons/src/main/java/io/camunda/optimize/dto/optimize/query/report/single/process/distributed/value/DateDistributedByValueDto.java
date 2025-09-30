/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.process.distributed.value;

import io.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import java.util.Objects;

public class DateDistributedByValueDto implements ProcessReportDistributedByValueDto {

  protected AggregateByDateUnit unit;

  public DateDistributedByValueDto() {}

  public AggregateByDateUnit getUnit() {
    return unit;
  }

  public void setUnit(final AggregateByDateUnit unit) {
    this.unit = unit;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof DateDistributedByValueDto;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DateDistributedByValueDto that = (DateDistributedByValueDto) o;
    return unit == that.unit;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(unit);
  }

  @Override
  public String toString() {
    return "DateDistributedByValueDto(unit=" + getUnit() + ")";
  }
}
