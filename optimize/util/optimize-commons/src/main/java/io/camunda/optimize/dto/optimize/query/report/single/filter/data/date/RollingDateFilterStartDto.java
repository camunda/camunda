/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.filter.data.date;

public class RollingDateFilterStartDto {

  protected Long value;
  protected DateUnit unit;

  public RollingDateFilterStartDto(final Long value, final DateUnit unit) {
    this.value = value;
    this.unit = unit;
  }

  public RollingDateFilterStartDto() {}

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
    return other instanceof RollingDateFilterStartDto;
  }

  @Override
  public int hashCode() {
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  @SuppressWarnings("checkstyle:ConstantName")
  public static final class Fields {

    public static final String value = "value";
    public static final String unit = "unit";
  }
}
