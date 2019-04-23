/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.configuration;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Getter
@Setter
public class ColumnOrderDto {

  private List<String> inputVariables = new ArrayList<>();
  private List<String> instanceProps = new ArrayList<>();
  private List<String> outputVariables = new ArrayList<>();
  private List<String> variables = new ArrayList<>();

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ColumnOrderDto)) {
      return false;
    }
    ColumnOrderDto that = (ColumnOrderDto) o;
    return Objects.equals(inputVariables, that.inputVariables) &&
      Objects.equals(instanceProps, that.instanceProps) &&
      Objects.equals(outputVariables, that.outputVariables) &&
      Objects.equals(variables, that.variables);
  }

  @Override
  public int hashCode() {
    return Objects.hash(inputVariables, instanceProps, outputVariables, variables);
  }
}
