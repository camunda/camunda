/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ColumnOrderDto {

  private List<String> inputVariables = new ArrayList<>();
  private List<String> instanceProps = new ArrayList<>();
  private List<String> outputVariables = new ArrayList<>();
  private List<String> variables = new ArrayList<>();

  public List<String> getInputVariables() {
    return inputVariables;
  }

  public void setInputVariables(List<String> inputVariables) {
    this.inputVariables = inputVariables;
  }

  public List<String> getInstanceProps() {
    return instanceProps;
  }

  public void setInstanceProps(List<String> instanceProps) {
    this.instanceProps = instanceProps;
  }

  public List<String> getOutputVariables() {
    return outputVariables;
  }

  public void setOutputVariables(List<String> outputVariables) {
    this.outputVariables = outputVariables;
  }

  public List<String> getVariables() {
    return variables;
  }

  public void setVariables(List<String> variables) {
    this.variables = variables;
  }

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
