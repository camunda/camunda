/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.engine;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;
import java.util.Optional;

@Data
public class DecisionDefinitionEngineDto implements Serializable, EngineDto {

  protected String id;
  protected String key;
  protected String category;
  protected String name;
  protected int version;
  protected String resource;
  protected String deploymentId;
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