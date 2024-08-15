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
    final int PRIME = 59;
    int result = 1;
    final Object $variableLabel = getVariableLabel();
    result = result * PRIME + ($variableLabel == null ? 43 : $variableLabel.hashCode());
    final Object $variableName = getVariableName();
    result = result * PRIME + ($variableName == null ? 43 : $variableName.hashCode());
    final Object $variableType = getVariableType();
    result = result * PRIME + ($variableType == null ? 43 : $variableType.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof LabelDto)) {
      return false;
    }
    final LabelDto other = (LabelDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$variableLabel = getVariableLabel();
    final Object other$variableLabel = other.getVariableLabel();
    if (this$variableLabel == null
        ? other$variableLabel != null
        : !this$variableLabel.equals(other$variableLabel)) {
      return false;
    }
    final Object this$variableName = getVariableName();
    final Object other$variableName = other.getVariableName();
    if (this$variableName == null
        ? other$variableName != null
        : !this$variableName.equals(other$variableName)) {
      return false;
    }
    final Object this$variableType = getVariableType();
    final Object other$variableType = other.getVariableType();
    if (this$variableType == null
        ? other$variableType != null
        : !this$variableType.equals(other$variableType)) {
      return false;
    }
    return true;
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

  public static final class Fields {

    public static final String variableLabel = "variableLabel";
    public static final String variableName = "variableName";
    public static final String variableType = "variableType";
  }
}
