/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.api.rest.v1.entities;

import io.camunda.tasklist.webapp.dto.VariableInputDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

public class StartProcessRequest {
  @Schema(description = "Variables to be passed when starting the process")
  private List<VariableInputDTO> variables = new ArrayList<>();

  public List<VariableInputDTO> getVariables() {
    return variables;
  }

  public StartProcessRequest setVariables(final List<VariableInputDTO> variables) {
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
