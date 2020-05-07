/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.engine.definition;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Optional;

@EqualsAndHashCode(callSuper = true)
@Data
public class DecisionDefinitionEngineDto extends DefinitionEngineDto {

  protected String key;
  protected String category;
  protected String name;
  protected int version;
  protected String resource;
  protected String tenantId;
  protected String decisionRequirementsDefinitionId;
  protected String decisionRequirementsDefinitionKey;
  protected Integer historyTimeToLive;
  protected String versionTag;

  @JsonIgnore
  public String getVersionAsString() {
    return String.valueOf(version);
  }

  public Optional<String> getTenantId() {
    return Optional.ofNullable(tenantId);
  }
}