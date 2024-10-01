/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.alert;

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
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    result = result * PRIME + getValue();
    final Object $unit = getUnit();
    result = result * PRIME + ($unit == null ? 43 : $unit.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof AlertInterval)) {
      return false;
    }
    final AlertInterval other = (AlertInterval) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (getValue() != other.getValue()) {
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
    return "AlertInterval(value=" + getValue() + ", unit=" + getUnit() + ")";
  }

  public static final class Fields {

    public static final String value = "value";
    public static final String unit = "unit";
  }
}
