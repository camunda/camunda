/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.process.distributed.value;

import io.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;

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
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $unit = getUnit();
    result = result * PRIME + ($unit == null ? 43 : $unit.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof DateDistributedByValueDto)) {
      return false;
    }
    final DateDistributedByValueDto other = (DateDistributedByValueDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$unit = getUnit();
    final Object other$unit = other.getUnit();
    if (this$unit == null ? other$unit != null : !this$unit.equals(other$unit)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "DateDistributedByValueDto(unit=" + getUnit() + ")";
  }
}
