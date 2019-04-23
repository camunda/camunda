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
}
