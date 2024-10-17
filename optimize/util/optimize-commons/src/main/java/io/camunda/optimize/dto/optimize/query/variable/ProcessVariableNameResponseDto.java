/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.variable;

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
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
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
