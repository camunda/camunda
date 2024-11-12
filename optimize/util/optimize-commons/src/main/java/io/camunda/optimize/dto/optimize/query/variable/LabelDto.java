/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.variable;

import io.camunda.optimize.dto.optimize.OptimizeDto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class LabelDto implements OptimizeDto {

  private String variableLabel;
  @NotBlank private String variableName;
  @NotNull private VariableType variableType;

  public LabelDto(
      final String variableLabel,
      @NotBlank final String variableName,
      @NotNull final VariableType variableType) {
    this.variableLabel = variableLabel;
    this.variableName = variableName;
    this.variableType = variableType;
  }

  public LabelDto() {}

  public String getVariableLabel() {
    return variableLabel;
  }

  public void setVariableLabel(final String variableLabel) {
    this.variableLabel = variableLabel;
  }

  public @NotBlank String getVariableName() {
    return variableName;
  }

  public void setVariableName(@NotBlank final String variableName) {
    this.variableName = variableName;
  }

  public @NotNull VariableType getVariableType() {
    return variableType;
  }

  public void setVariableType(@NotNull final VariableType variableType) {
    this.variableType = variableType;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof LabelDto;
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
    return "LabelDto(variableLabel="
        + getVariableLabel()
        + ", variableName="
        + getVariableName()
        + ", variableType="
        + getVariableType()
        + ")";
  }

  @SuppressWarnings("checkstyle:ConstantName")
  public static final class Fields {

    public static final String variableLabel = "variableLabel";
    public static final String variableName = "variableName";
    public static final String variableType = "variableType";
  }
}
