/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.importing;

import io.camunda.optimize.dto.optimize.query.variable.VariableType;

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
      final String id,
      final String clauseId,
      final String clauseName,
      final String ruleId,
      final Integer ruleOrder,
      final String variableName,
      final VariableType type,
      final String value) {
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

  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public String getClauseId() {
    return clauseId;
  }

  public void setClauseId(final String clauseId) {
    this.clauseId = clauseId;
  }

  public String getClauseName() {
    return clauseName;
  }

  public void setClauseName(final String clauseName) {
    this.clauseName = clauseName;
  }

  public String getRuleId() {
    return ruleId;
  }

  public void setRuleId(final String ruleId) {
    this.ruleId = ruleId;
  }

  public Integer getRuleOrder() {
    return ruleOrder;
  }

  public void setRuleOrder(final Integer ruleOrder) {
    this.ruleOrder = ruleOrder;
  }

  public String getVariableName() {
    return variableName;
  }

  public void setVariableName(final String variableName) {
    this.variableName = variableName;
  }

  public VariableType getType() {
    return type;
  }

  public void setType(final VariableType type) {
    this.type = type;
  }

  public String getValue() {
    return value;
  }

  public void setValue(final String value) {
    this.value = value;
  }
}
