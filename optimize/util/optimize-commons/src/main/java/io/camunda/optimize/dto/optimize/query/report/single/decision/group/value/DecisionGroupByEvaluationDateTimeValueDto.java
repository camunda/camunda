/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.decision.group.value;

import io.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import java.util.Objects;

public class DecisionGroupByEvaluationDateTimeValueDto implements DecisionGroupByValueDto {

  protected AggregateByDateUnit unit;

  public DecisionGroupByEvaluationDateTimeValueDto() {}

  @Override
  public boolean isCombinable(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof DecisionGroupByEvaluationDateTimeValueDto)) {
      return false;
    }
    final DecisionGroupByEvaluationDateTimeValueDto that =
        (DecisionGroupByEvaluationDateTimeValueDto) o;
    return Objects.equals(unit, that.unit);
  }

  public AggregateByDateUnit getUnit() {
    return unit;
  }

  public void setUnit(final AggregateByDateUnit unit) {
    this.unit = unit;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof DecisionGroupByEvaluationDateTimeValueDto;
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
    if (!(o instanceof DecisionGroupByEvaluationDateTimeValueDto)) {
      return false;
    }
    final DecisionGroupByEvaluationDateTimeValueDto other =
        (DecisionGroupByEvaluationDateTimeValueDto) o;
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
    return "DecisionGroupByEvaluationDateTimeValueDto(unit=" + getUnit() + ")";
  }
}
