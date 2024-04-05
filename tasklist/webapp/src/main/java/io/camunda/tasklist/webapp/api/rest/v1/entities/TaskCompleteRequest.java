/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.api.rest.v1.entities;

import io.camunda.tasklist.webapp.graphql.entity.VariableInputDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

@Schema(
    description =
        "Request object with variables to update or add to task during the task completion.")
public class TaskCompleteRequest {

  @Schema(description = "Variables to update or add to task during the task completion")
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
