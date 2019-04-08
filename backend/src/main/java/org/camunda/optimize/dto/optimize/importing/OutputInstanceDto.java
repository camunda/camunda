/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.importing;

import org.camunda.optimize.dto.optimize.query.variable.VariableType;

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

  public String getId() {
    return id;
  }

  public String getClauseId() {
    return clauseId;
  }

  public String getClauseName() {
    return clauseName;
  }

  public String getRuleId() {
    return ruleId;
  }

  public Integer getRuleOrder() {
    return ruleOrder;
  }

  public String getVariableName() {
    return variableName;
  }

  public VariableType getType() {
    return type;
  }

  public String getValue() {
    return value;
  }

  public void setId(String id) {
    this.id = id;
  }

  public void setClauseId(String clauseId) {
    this.clauseId = clauseId;
  }

  public void setClauseName(String clauseName) {
    this.clauseName = clauseName;
  }

  public void setRuleId(String ruleId) {
    this.ruleId = ruleId;
  }

  public void setRuleOrder(Integer ruleOrder) {
    this.ruleOrder = ruleOrder;
  }

  public void setVariableName(String variableName) {
    this.variableName = variableName;
  }

  public void setType(VariableType type) {
    this.type = type;
  }

  public void setValue(String value) {
    this.value = value;
  }
}
