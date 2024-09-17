/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.decision.result.raw;

import io.camunda.optimize.dto.optimize.query.report.single.RawDataInstanceDto;
import java.time.OffsetDateTime;
import java.util.Map;
import lombok.Data;

@Data
public class RawDataDecisionInstanceDto implements RawDataInstanceDto {

  protected String decisionDefinitionKey;
  protected String decisionDefinitionId;
  protected String decisionInstanceId;
  protected String processInstanceId;
  protected OffsetDateTime evaluationDateTime;
  protected String engineName;
  protected String tenantId;
  protected Map<String, InputVariableEntry> inputVariables;
  protected Map<String, OutputVariableEntry> outputVariables;

  public RawDataDecisionInstanceDto(
      String decisionDefinitionKey,
      String decisionDefinitionId,
      String decisionInstanceId,
      String processInstanceId,
      OffsetDateTime evaluationDateTime,
      String engineName,
      String tenantId,
      Map<String, InputVariableEntry> inputVariables,
      Map<String, OutputVariableEntry> outputVariables) {
    this.decisionDefinitionKey = decisionDefinitionKey;
    this.decisionDefinitionId = decisionDefinitionId;
    this.decisionInstanceId = decisionInstanceId;
    this.processInstanceId = processInstanceId;
    this.evaluationDateTime = evaluationDateTime;
    this.engineName = engineName;
    this.tenantId = tenantId;
    this.inputVariables = inputVariables;
    this.outputVariables = outputVariables;
  }

  public RawDataDecisionInstanceDto() {}

  public enum Fields {
    decisionDefinitionKey,
    decisionDefinitionId,
    decisionInstanceId,
    processInstanceId,
    evaluationDateTime,
    engineName,
    tenantId
  }
}
