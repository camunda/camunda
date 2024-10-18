/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize;

public class FlowNodeTotalDurationDataDto {

  String name;
  long value;

  public FlowNodeTotalDurationDataDto(final String name, final long value) {
    this.name = name;
    this.value = value;
  }

  public FlowNodeTotalDurationDataDto() {}

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public long getValue() {
    return value;
  }

  public void setValue(final long value) {
    this.value = value;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof FlowNodeTotalDurationDataDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $name = getName();
    result = result * PRIME + ($name == null ? 43 : $name.hashCode());
    final long $value = getValue();
    result = result * PRIME + (int) ($value >>> 32 ^ $value);
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof FlowNodeTotalDurationDataDto)) {
      return false;
    }
    final FlowNodeTotalDurationDataDto other = (FlowNodeTotalDurationDataDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$name = getName();
    final Object other$name = other.getName();
    if (this$name == null ? other$name != null : !this$name.equals(other$name)) {
      return false;
    }
    if (getValue() != other.getValue()) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "FlowNodeTotalDurationDataDto(name=" + getName() + ", value=" + getValue() + ")";
  }
}
