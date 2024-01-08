/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.api.rest.v1.entities;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

public class VariablesSearchRequest {
  private List<String> variableNames = new ArrayList<>();

  private List<IncludeVariable> includeVariables = new ArrayList<>();

  public List<String> getVariableNames() {
    return variableNames;
  }

  public VariablesSearchRequest setVariableNames(List<String> variableNames) {
    this.variableNames = variableNames;
    return this;
  }

  public List<IncludeVariable> getIncludeVariables() {
    return includeVariables;
  }

  public VariablesSearchRequest setIncludeVariables(List<IncludeVariable> includeVariables) {
    this.includeVariables = includeVariables;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final VariablesSearchRequest that = (VariablesSearchRequest) o;
    return Objects.equals(variableNames, that.variableNames)
        && Objects.equals(includeVariables, that.includeVariables);
  }

  @Override
  public int hashCode() {
    return Objects.hash(variableNames, includeVariables);
  }

  @Override
  public String toString() {
    return new StringJoiner(", ", VariablesSearchRequest.class.getSimpleName() + "[", "]")
        .add("variableNames=" + variableNames)
        .add("includeVariables=" + includeVariables)
        .toString();
  }
}
