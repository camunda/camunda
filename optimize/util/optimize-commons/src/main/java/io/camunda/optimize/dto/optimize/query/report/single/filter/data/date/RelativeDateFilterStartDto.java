/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.filter.data.date;

import java.util.Objects;

public class RelativeDateFilterStartDto {

  protected Long value;
  protected DateUnit unit;

  public RelativeDateFilterStartDto(final Long value, final DateUnit unit) {
    this.value = value;
    this.unit = unit;
  }

  public RelativeDateFilterStartDto() {}

  public Long getValue() {
    return value;
  }

  public void setValue(final Long value) {
    this.value = value;
  }

  public DateUnit getUnit() {
    return unit;
  }

  public void setUnit(final DateUnit unit) {
    this.unit = unit;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof RelativeDateFilterStartDto;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final RelativeDateFilterStartDto that = (RelativeDateFilterStartDto) o;
    return Objects.equals(value, that.value) && unit == that.unit;
  }

  @Override
  public int hashCode() {
    return Objects.hash(value, unit);
  }

  @SuppressWarnings("checkstyle:ConstantName")
  public static final class Fields {

    public static final String value = "value";
    public static final String unit = "unit";
  }
}
