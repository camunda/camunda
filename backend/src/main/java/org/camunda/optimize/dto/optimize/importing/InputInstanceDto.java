/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.importing;

import org.camunda.optimize.dto.optimize.query.variable.VariableType;

public class InputInstanceDto {
  private String id;
  private String clauseId;
  private String clauseName;
  private VariableType type;
  private String value;

  public InputInstanceDto() {
  }

  public InputInstanceDto(final String id, final String clauseId, final String clauseName, final VariableType type,
                           final String value) {
    this.id = id;
    this.clauseId = clauseId;
    this.clauseName = clauseName;
    this.type = type;
    this.value = value;
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
