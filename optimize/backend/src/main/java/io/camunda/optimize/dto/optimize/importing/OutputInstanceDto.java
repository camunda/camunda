/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.importing;

import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OutputInstanceDto {

  private String id;
  private String clauseId;
  private String clauseName;
  private String ruleId;
  private Integer ruleOrder;
  private String variableName;
  private VariableType type;
  private String value;

  public OutputInstanceDto(
      String id,
      String clauseId,
      String clauseName,
      String ruleId,
      Integer ruleOrder,
      String variableName,
      VariableType type,
      String value) {
    this.id = id;
    this.clauseId = clauseId;
    this.clauseName = clauseName;
    this.ruleId = ruleId;
    this.ruleOrder = ruleOrder;
    this.variableName = variableName;
    this.type = type;
    this.value = value;
  }

  public OutputInstanceDto() {}
}
