
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.importing;

import io.camunda.optimize.dto.optimize.OptimizeDto;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Data;

@Data
public class DecisionInstanceDto implements OptimizeDto {

  private String decisionInstanceId;
  private String processDefinitionId;
  private String processDefinitionKey;
  private String decisionDefinitionId;
  private String decisionDefinitionKey;
  private String decisionDefinitionVersion;
  private OffsetDateTime evaluationDateTime;
  private String processInstanceId;
  private String rootProcessInstanceId;
  private String activityId;
  private Double collectResultValue;
  private String rootDecisionInstanceId;
  private List<InputInstanceDto> inputs = new ArrayList<>();
  private List<OutputInstanceDto> outputs = new ArrayList<>();
  private Set<String> matchedRules = new HashSet<>();
  private String engine;
  private String tenantId;

  public DecisionInstanceDto(String decisionInstanceId, String processDefinitionId,
      String processDefinitionKey, String decisionDefinitionId, String decisionDefinitionKey,
      String decisionDefinitionVersion, OffsetDateTime evaluationDateTime, String processInstanceId,
      String rootProcessInstanceId, String activityId, Double collectResultValue,
      String rootDecisionInstanceId, List<InputInstanceDto> inputs, List<OutputInstanceDto> outputs,
      Set<String> matchedRules, String engine, String tenantId) {
    this.decisionInstanceId = decisionInstanceId;
    this.processDefinitionId = processDefinitionId;
    this.processDefinitionKey = processDefinitionKey;
    this.decisionDefinitionId = decisionDefinitionId;
    this.decisionDefinitionKey = decisionDefinitionKey;
    this.decisionDefinitionVersion = decisionDefinitionVersion;
    this.evaluationDateTime = evaluationDateTime;
    this.processInstanceId = processInstanceId;
    this.rootProcessInstanceId = rootProcessInstanceId;
    this.activityId = activityId;
    this.collectResultValue = collectResultValue;
    this.rootDecisionInstanceId = rootDecisionInstanceId;
    this.inputs = inputs;
    this.outputs = outputs;
    this.matchedRules = matchedRules;
    this.engine = engine;
    this.tenantId = tenantId;
  }

  public DecisionInstanceDto() {
  }

  public static final class Fields {

    public static final String decisionInstanceId = "decisionInstanceId";
    public static final String processDefinitionId = "processDefinitionId";
    public static final String processDefinitionKey = "processDefinitionKey";
    public static final String decisionDefinitionId = "decisionDefinitionId";
    public static final String decisionDefinitionKey = "decisionDefinitionKey";
    public static final String decisionDefinitionVersion = "decisionDefinitionVersion";
    public static final String evaluationDateTime = "evaluationDateTime";
    public static final String processInstanceId = "processInstanceId";
    public static final String rootProcessInstanceId = "rootProcessInstanceId";
    public static final String activityId = "activityId";
    public static final String collectResultValue = "collectResultValue";
    public static final String rootDecisionInstanceId = "rootDecisionInstanceId";
    public static final String inputs = "inputs";
    public static final String outputs = "outputs";
    public static final String matchedRules = "matchedRules";
    public static final String engine = "engine";
    public static final String tenantId = "tenantId";
  }
}
