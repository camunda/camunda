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
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
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
