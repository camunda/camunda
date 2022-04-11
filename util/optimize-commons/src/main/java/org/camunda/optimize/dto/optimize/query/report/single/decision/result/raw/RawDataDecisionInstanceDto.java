/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.query.report.single.RawDataInstanceDto;

import java.time.OffsetDateTime;
import java.util.Map;

@AllArgsConstructor
@NoArgsConstructor
@Data
@FieldNameConstants(asEnum = true)
public class RawDataDecisionInstanceDto implements RawDataInstanceDto {
  protected String decisionDefinitionKey;
  protected String decisionDefinitionId;
  protected String decisionInstanceId;
  protected String processInstanceId;
  protected OffsetDateTime evaluationDateTime;
  protected String engineName;
  protected String tenantId;
  @FieldNameConstants.Exclude
  protected Map<String, InputVariableEntry> inputVariables;
  @FieldNameConstants.Exclude
  protected Map<String, OutputVariableEntry> outputVariables;
}
