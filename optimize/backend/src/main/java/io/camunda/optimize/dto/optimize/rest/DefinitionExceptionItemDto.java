/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest;

import io.camunda.optimize.dto.optimize.DefinitionType;
import java.io.Serializable;
import java.util.List;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@Builder
@EqualsAndHashCode
public class DefinitionExceptionItemDto implements Serializable {

  private DefinitionType type;
  private String key;
  private List<String> versions;
  private List<String> tenantIds;

  public DefinitionExceptionItemDto(
      DefinitionType type, String key, List<String> versions, List<String> tenantIds) {
    this.type = type;
    this.key = key;
    this.versions = versions;
    this.tenantIds = tenantIds;
  }

  public DefinitionExceptionItemDto() {}
}
