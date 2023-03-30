/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.api.rest.v1.entities;

import io.camunda.tasklist.webapp.graphql.entity.VariableInputDTO;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

public class TaskCompleteRequest {

  private List<VariableInputDTO> variables = new ArrayList<>();

  public List<VariableInputDTO> getVariables() {
    return variables;
  }

  public TaskCompleteRequest setVariables(List<VariableInputDTO> variables) {
    this.variables = variables;
    return this;
  }

  @Override
  public String toString() {
    return new StringJoiner(", ", TaskCompleteRequest.class.getSimpleName() + "[", "]")
        .add("variables=" + variables)
        .toString();
  }
}
