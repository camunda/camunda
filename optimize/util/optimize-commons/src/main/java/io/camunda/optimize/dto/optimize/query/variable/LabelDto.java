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
import lombok.Data;

@Data
public class LabelDto implements OptimizeDto {

  private String variableLabel;
  @NotBlank private String variableName;
  @NotNull private VariableType variableType;

  public LabelDto(
      String variableLabel, @NotBlank String variableName, @NotNull VariableType variableType) {
    this.variableLabel = variableLabel;
    this.variableName = variableName;
    this.variableType = variableType;
  }

  public LabelDto() {}

  public static final class Fields {

    public static final String variableLabel = "variableLabel";
    public static final String variableName = "variableName";
    public static final String variableType = "variableType";
  }
}
