/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.analysis;

import java.util.Objects;

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
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final FlowNodeOutlierVariableParametersDto that = (FlowNodeOutlierVariableParametersDto) o;
    return Objects.equals(variableName, that.variableName)
        && Objects.equals(variableTerm, that.variableTerm);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), variableName, variableTerm);
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof FlowNodeOutlierVariableParametersDto;
  }
}
