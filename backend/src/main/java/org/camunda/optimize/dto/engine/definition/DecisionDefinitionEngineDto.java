/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.engine.definition;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class DecisionDefinitionEngineDto extends DefinitionEngineDto {

  protected String key;
  protected String category;
  protected String name;
  protected int version;
  protected String resource;
  protected String decisionRequirementsDefinitionId;
  protected String decisionRequirementsDefinitionKey;
  protected Integer historyTimeToLive;
  protected String versionTag;

  @JsonIgnore
  public String getVersionAsString() {
    return String.valueOf(version);
  }
}