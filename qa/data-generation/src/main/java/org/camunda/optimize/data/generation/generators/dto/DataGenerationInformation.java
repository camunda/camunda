/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.data.generation.generators.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class DataGenerationInformation {
  private Long processInstanceCountToGenerate;
  private Long decisionInstanceCountToGenerate;
  @Builder.Default
  private String engineRestEndpoint = "http://localhost:8080/engine-rest";
  private boolean removeDeployments;
  private Map<String, Integer> processDefinitionsAndNumberOfVersions;
  private Map<String, Integer> decisionDefinitionsAndNumberOfVersions;
}

