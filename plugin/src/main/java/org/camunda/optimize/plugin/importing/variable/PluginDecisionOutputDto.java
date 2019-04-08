/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.plugin.importing.variable;

public class PluginDecisionOutputDto {

  /**
   * The id of the decision output value.
   */
  private String id;

  /**
   * The id of the clause the output value belongs to.
   */
  private String clauseId;

  /**
   * The name of the clause the output value belongs to.
   */
  private String clauseName;

  /**
   * The id of the rule the output value belongs to.
   */
  private String ruleId;

  /**
   * The order of the rule the output value belongs to.
   */
  private Integer ruleOrder;

  /**
   * The name of the output variable.
   */
  private String variableName;

  /**
   * The value type of the variable.
   * Only simple variable types (i.e. Boolean, String, Date, Long, Short, Integer, Double) can be imported to optimize.
   * Make sure to set the variable type to one of these values while adapting the outputs
   */
  private String type;

  /**
   * A string representation of the output variable's value.
   */
  private String value;

  /**
   * The key of the decision definition, current output corresponds to.
   */
  private String decisionDefinitionKey;

  /**
   * The version of the decision definition, current output corresponds to.
   */
  private String decisionDefinitionVersion;

  /**
   * The ID of the decision definition, current output corresponds to.
   */
  private String decisionDefinitionId;


  /**
   * The ID of the decision instance, current output corresponds to.
   */
  private String decisionInstanceId;

  /**
   * Alias of the engine, from which the current Decision Instance is imported.
   */
  private String engineAlias;

  public PluginDecisionOutputDto() {
  }

  public PluginDecisionOutputDto(String id, String clauseId, String clauseName, String ruleId, Integer ruleOrder, String
    variableName, String type, String value, String decisionDefinitionKey, String decisionDefinitionVersion, String
                                   decisionDefinitionId, String decisionInstanceId, String engineAlias) {
    this.id = id;
    this.clauseId = clauseId;
    this.clauseName = clauseName;
    this.ruleId = ruleId;
    this.ruleOrder = ruleOrder;
    this.variableName = variableName;
    this.type = type;
    this.value = value;
    this.decisionDefinitionKey = decisionDefinitionKey;
    this.decisionDefinitionVersion = decisionDefinitionVersion;
    this.decisionDefinitionId = decisionDefinitionId;
    this.decisionInstanceId = decisionInstanceId;
    this.engineAlias = engineAlias;
  }

  public String getDecisionDefinitionKey() {
    return decisionDefinitionKey;
  }

  public void setDecisionDefinitionKey(String decisionDefinitionKey) {
    this.decisionDefinitionKey = decisionDefinitionKey;
  }

  public String getDecisionDefinitionVersion() {
    return decisionDefinitionVersion;
  }

  public void setDecisionDefinitionVersion(String decisionDefinitionVersion) {
    this.decisionDefinitionVersion = decisionDefinitionVersion;
  }

  public String getDecisionDefinitionId() {
    return decisionDefinitionId;
  }

  public void setDecisionDefinitionId(String decisionDefinitionId) {
    this.decisionDefinitionId = decisionDefinitionId;
  }

  public String getDecisionInstanceId() {
    return decisionInstanceId;
  }

  public void setDecisionInstanceId(String decisionInstanceId) {
    this.decisionInstanceId = decisionInstanceId;
  }

  public String getEngineAlias() {
    return engineAlias;
  }

  public void setEngineAlias(String engineAlias) {
    this.engineAlias = engineAlias;
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

  public String getType() {
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

  public void setType(String type) {
    this.type = type;
  }

  public void setValue(String value) {
    this.value = value;
  }
}