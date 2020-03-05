/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.data.generation.generators.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.HashMap;

@Data
@Accessors(chain = true)
public class DataGenerationInformation {

  private Long processInstanceCountToGenerate;
  private Long decisionInstanceCountToGenerate;
  private String engineRestEndpoint;
  private boolean removeDeployments;
  private HashMap<String, Integer> processDefinitions;
  private HashMap<String, Integer> decisionDefinitions;
}

