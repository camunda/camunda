/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.plugin.importing.variable;

public class PluginDecisionInputDto {

  /**
   * Unique id of the decision input value.
   */
  private String id;

  /**
   * The id of the clause the input value belongs to.
   */
  private String clauseId;

  /**
   * The name of the clause the input value belongs to.
   */
  private String clauseName;

  /**
   * The value type of the variable.
   * Only simple variable types (i.e. Boolean, String, Date, Long, Short, Integer, Double) can be imported to optimize.
   * Make sure to set the variable type to one of these values while adapting the inputs
   */
  private String type;

  /**
   * The string representation of the input variable's value.
   */
  private String value;

  /**
   * The key of the decision definition, current input corresponds to.
   */
  private String decisionDefinitionKey;

  /**
   * The version of the decision definition, current input corresponds to.
   */
  private String decisionDefinitionVersion;

  /**
   * The ID of the decision definition, current input corresponds to.
   */
  private String decisionDefinitionId;


  /**
   * The ID of the decision instance, current input corresponds to.
   */
  private String decisionInstanceId;

  /**
   * Alias of the engine, from which the current Decision Instance is imported.
   */
  private String engineAlias;

  public PluginDecisionInputDto() {
  }

  public PluginDecisionInputDto(String id, String clauseId, String clauseName, String type, String value, String
    decisionDefinitionKey, String decisionDefinitionVersion, String decisionDefinitionId, String decisionInstanceId,
                                String engineAlias) {
    this.id = id;
    this.clauseId = clauseId;
    this.clauseName = clauseName;
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

  public String getType() {
    return type;
  }

  public void setType(final String type) {
    this.type = type;
  }

  public String getValue() {
    return value;
  }

  public void setValue(final String value) {
    this.value = value;
  }
}
