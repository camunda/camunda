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
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
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
