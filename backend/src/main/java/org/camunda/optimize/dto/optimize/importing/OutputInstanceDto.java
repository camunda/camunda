/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.importing;

import lombok.Getter;
import lombok.Setter;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;

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

  public OutputInstanceDto() {
  }

  public OutputInstanceDto(final String id, final String clauseId, final String clauseName, final String ruleId,
                           final Integer ruleOrder, final String variableName, final VariableType type, final String value) {
    this.id = id;
    this.clauseId = clauseId;
    this.clauseName = clauseName;
    this.ruleId = ruleId;
    this.ruleOrder = ruleOrder;
    this.variableName = variableName;
    this.type = type;
    this.value = value;
  }
}
