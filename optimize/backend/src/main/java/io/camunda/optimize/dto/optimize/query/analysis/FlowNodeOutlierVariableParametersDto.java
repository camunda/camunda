/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.analysis;

public class FlowNodeOutlierVariableParametersDto extends FlowNodeOutlierParametersDto {

  protected String variableName;
  protected String variableTerm;

  public FlowNodeOutlierVariableParametersDto() {}

  public String getVariableName() {
    return variableName;
  }

  public void setVariableName(final String variableName) {
    this.variableName = variableName;
  }

  public String getVariableTerm() {
    return variableTerm;
  }

  public void setVariableTerm(final String variableTerm) {
    this.variableTerm = variableTerm;
  }

  @Override
  public String toString() {
    return "FlowNodeOutlierVariableParametersDto(variableName="
        + getVariableName()
        + ", variableTerm="
        + getVariableTerm()
        + ")";
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof FlowNodeOutlierVariableParametersDto)) {
      return false;
    }
    final FlowNodeOutlierVariableParametersDto other = (FlowNodeOutlierVariableParametersDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final Object this$variableName = getVariableName();
    final Object other$variableName = other.getVariableName();
    if (this$variableName == null
        ? other$variableName != null
        : !this$variableName.equals(other$variableName)) {
      return false;
    }
    final Object this$variableTerm = getVariableTerm();
    final Object other$variableTerm = other.getVariableTerm();
    if (this$variableTerm == null
        ? other$variableTerm != null
        : !this$variableTerm.equals(other$variableTerm)) {
      return false;
    }
    return true;
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof FlowNodeOutlierVariableParametersDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = super.hashCode();
    final Object $variableName = getVariableName();
    result = result * PRIME + ($variableName == null ? 43 : $variableName.hashCode());
    final Object $variableTerm = getVariableTerm();
    result = result * PRIME + ($variableTerm == null ? 43 : $variableTerm.hashCode());
    return result;
  }
}
