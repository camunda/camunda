/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.util.client.dto;

import io.camunda.optimize.dto.optimize.query.variable.VariableType;

public class VariableValueDto {

  private Object value;
  private VariableType type;

  public VariableValueDto(final Object value, final VariableType type) {
    this.value = value;
    this.type = type;
  }

  public VariableValueDto() {}

  public Object getValue() {
    return value;
  }

  public void setValue(final Object value) {
    this.value = value;
  }

  public VariableType getType() {
    return type;
  }

  public void setType(final VariableType type) {
    this.type = type;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof VariableValueDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $value = getValue();
    result = result * PRIME + ($value == null ? 43 : $value.hashCode());
    final Object $type = getType();
    result = result * PRIME + ($type == null ? 43 : $type.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof VariableValueDto)) {
      return false;
    }
    final VariableValueDto other = (VariableValueDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$value = getValue();
    final Object other$value = other.getValue();
    if (this$value == null ? other$value != null : !this$value.equals(other$value)) {
      return false;
    }
    final Object this$type = getType();
    final Object other$type = other.getType();
    if (this$type == null ? other$type != null : !this$type.equals(other$type)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "VariableValueDto(value=" + getValue() + ", type=" + getType() + ")";
  }
}
