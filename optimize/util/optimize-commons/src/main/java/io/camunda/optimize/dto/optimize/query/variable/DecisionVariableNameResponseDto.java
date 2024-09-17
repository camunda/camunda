/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.variable;

import lombok.Data;

@Data
public class DecisionVariableNameResponseDto {

  protected String id;
  protected String name;
  protected VariableType type;

  public DecisionVariableNameResponseDto(String id, String name, VariableType type) {
    this.id = id;
    this.name = name;
    this.type = type;
  }

  public DecisionVariableNameResponseDto() {}
}
