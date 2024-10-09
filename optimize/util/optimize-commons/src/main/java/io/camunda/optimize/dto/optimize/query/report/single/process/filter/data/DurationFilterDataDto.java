/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.process.filter.data;

import io.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DurationUnit;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.ComparisonOperator;

public class DurationFilterDataDto implements FilterDataDto {

  protected Long value;
  protected DurationUnit unit;
  protected ComparisonOperator operator;
  protected boolean includeNull;

  public DurationFilterDataDto(
      final Long value,
      final DurationUnit unit,
      final ComparisonOperator operator,
      final boolean includeNull) {
    this.value = value;
    this.unit = unit;
    this.operator = operator;
    this.includeNull = includeNull;
  }

  public DurationFilterDataDto() {}

  public Long getValue() {
    return value;
  }

  public void setValue(final Long value) {
    this.value = value;
  }

  public DurationUnit getUnit() {
    return unit;
  }

  public void setUnit(final DurationUnit unit) {
    this.unit = unit;
  }

  public ComparisonOperator getOperator() {
    return operator;
  }

  public void setOperator(final ComparisonOperator operator) {
    this.operator = operator;
  }

  public boolean isIncludeNull() {
    return includeNull;
  }

  public void setIncludeNull(final boolean includeNull) {
    this.includeNull = includeNull;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof DurationFilterDataDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $value = getValue();
    result = result * PRIME + ($value == null ? 43 : $value.hashCode());
    final Object $unit = getUnit();
    result = result * PRIME + ($unit == null ? 43 : $unit.hashCode());
    final Object $operator = getOperator();
    result = result * PRIME + ($operator == null ? 43 : $operator.hashCode());
    result = result * PRIME + (isIncludeNull() ? 79 : 97);
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof DurationFilterDataDto)) {
      return false;
    }
    final DurationFilterDataDto other = (DurationFilterDataDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$value = getValue();
    final Object other$value = other.getValue();
    if (this$value == null ? other$value != null : !this$value.equals(other$value)) {
      return false;
    }
    final Object this$unit = getUnit();
    final Object other$unit = other.getUnit();
    if (this$unit == null ? other$unit != null : !this$unit.equals(other$unit)) {
      return false;
    }
    final Object this$operator = getOperator();
    final Object other$operator = other.getOperator();
    if (this$operator == null ? other$operator != null : !this$operator.equals(other$operator)) {
      return false;
    }
    if (isIncludeNull() != other.isIncludeNull()) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "DurationFilterDataDto(value="
        + getValue()
        + ", unit="
        + getUnit()
        + ", operator="
        + getOperator()
        + ", includeNull="
        + isIncludeNull()
        + ")";
  }

  public static DurationFilterDataDtoBuilder builder() {
    return new DurationFilterDataDtoBuilder();
  }

  public static class DurationFilterDataDtoBuilder {

    private Long value;
    private DurationUnit unit;
    private ComparisonOperator operator;
    private boolean includeNull;

    DurationFilterDataDtoBuilder() {}

    public DurationFilterDataDtoBuilder value(final Long value) {
      this.value = value;
      return this;
    }

    public DurationFilterDataDtoBuilder unit(final DurationUnit unit) {
      this.unit = unit;
      return this;
    }

    public DurationFilterDataDtoBuilder operator(final ComparisonOperator operator) {
      this.operator = operator;
      return this;
    }

    public DurationFilterDataDtoBuilder includeNull(final boolean includeNull) {
      this.includeNull = includeNull;
      return this;
    }

    public DurationFilterDataDto build() {
      return new DurationFilterDataDto(value, unit, operator, includeNull);
    }

    @Override
    public String toString() {
      return "DurationFilterDataDto.DurationFilterDataDtoBuilder(value="
          + value
          + ", unit="
          + unit
          + ", operator="
          + operator
          + ", includeNull="
          + includeNull
          + ")";
    }
  }
}
