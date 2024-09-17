/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.processoverview;

import lombok.Data;

@Data
public class InitialProcessOwnerDto {

  private String processDefinitionKey;
  private String owner;

  public InitialProcessOwnerDto(String processDefinitionKey, String owner) {
    this.processDefinitionKey = processDefinitionKey;
    this.owner = owner;
  }

  public InitialProcessOwnerDto() {}
}
