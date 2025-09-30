/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.util.client.dto;

import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import java.util.Objects;

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
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final VariableValueDto that = (VariableValueDto) o;
    return Objects.equals(value, that.value) && type == that.type;
  }

  @Override
  public int hashCode() {
    return Objects.hash(value, type);
  }

  @Override
  public String toString() {
    return "VariableValueDto(value=" + getValue() + ", type=" + getType() + ")";
  }
}
