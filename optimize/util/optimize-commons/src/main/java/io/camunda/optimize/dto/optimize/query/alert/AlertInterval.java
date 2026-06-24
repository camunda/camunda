/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.alert;

import java.util.Objects;

public class AlertInterval {

  private int value;
  private AlertIntervalUnit unit;

  public AlertInterval(final int value, final AlertIntervalUnit unit) {
    this.value = value;
    this.unit = unit;
  }

  public AlertInterval() {}

  public int getValue() {
    return value;
  }

  public void setValue(final int value) {
    this.value = value;
  }

  public AlertIntervalUnit getUnit() {
    return unit;
  }

  public void setUnit(final AlertIntervalUnit unit) {
    this.unit = unit;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof AlertInterval;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final AlertInterval that = (AlertInterval) o;
    return value == that.value && unit == that.unit;
  }

  @Override
  public int hashCode() {
    return Objects.hash(value, unit);
  }

  @Override
  public String toString() {
    return "AlertInterval(value=" + getValue() + ", unit=" + getUnit() + ")";
  }

  @SuppressWarnings("checkstyle:ConstantName")
  public static final class Fields {

    public static final String value = "value";
    public static final String unit = "unit";
  }
}
