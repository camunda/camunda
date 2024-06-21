/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.data.generation.generators.dto;

import java.util.Map;
import lombok.Builder;
import lombok.Data;

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
