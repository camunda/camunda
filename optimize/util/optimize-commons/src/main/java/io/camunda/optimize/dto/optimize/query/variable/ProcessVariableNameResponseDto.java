/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.variable;

import java.util.Objects;

public class ProcessVariableNameResponseDto {

  protected String name;
  protected VariableType type;
  protected String label;

  public ProcessVariableNameResponseDto(
      final String name, final VariableType type, final String label) {
    this.name = name;
    this.type = type;
    this.label = label;
  }

  public ProcessVariableNameResponseDto() {}

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public VariableType getType() {
    return type;
  }

  public void setType(final VariableType type) {
    this.type = type;
  }

  public String getLabel() {
    return label;
  }

  public void setLabel(final String label) {
    this.label = label;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ProcessVariableNameResponseDto;
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, type, label);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ProcessVariableNameResponseDto that = (ProcessVariableNameResponseDto) o;
    return Objects.equals(name, that.name)
        && Objects.equals(type, that.type)
        && Objects.equals(label, that.label);
  }

  @Override
  public String toString() {
    return "ProcessVariableNameResponseDto(name="
        + getName()
        + ", type="
        + getType()
        + ", label="
        + getLabel()
        + ")";
  }
}
