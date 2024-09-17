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
public class InputInstanceDto {

  private String id;
  private String clauseId;
  private String clauseName;
  private VariableType type;
  private String value;

  public InputInstanceDto(
      String id, String clauseId, String clauseName, VariableType type, String value) {
    this.id = id;
    this.clauseId = clauseId;
    this.clauseName = clauseName;
    this.type = type;
    this.value = value;
  }

  public InputInstanceDto() {}
}
