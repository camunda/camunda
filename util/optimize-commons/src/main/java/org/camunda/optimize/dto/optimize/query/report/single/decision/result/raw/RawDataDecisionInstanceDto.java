/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Map;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class RawDataDecisionInstanceDto {
  protected String decisionDefinitionKey;
  protected String decisionDefinitionId;
  protected String decisionInstanceId;
  protected OffsetDateTime evaluationDateTime;
  protected String engineName;
  protected String tenantId;
  protected Map<String, InputVariableEntry> inputVariables;
  protected Map<String, OutputVariableEntry> outputVariables;
}
