/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.engine;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.Data;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class HistoricDecisionInstanceDto implements TenantSpecificEngineDto {

  private String id;
  private String decisionDefinitionId;
  private String decisionDefinitionKey;
  private String decisionDefinitionName;
  private OffsetDateTime evaluationTime;
  private String processDefinitionId;
  private String processDefinitionKey;
  private String processInstanceId;
  private String rootProcessInstanceId;
  private String caseDefinitionId;
  private String caseDefinitionKey;
  private String caseInstanceId;
  private String activityId;
  private String activityInstanceId;
  private String userId;
  private List<HistoricDecisionInputInstanceDto> inputs = new ArrayList<>();
  private List<HistoricDecisionOutputInstanceDto> outputs = new ArrayList<>();
  private Double collectResultValue;
  private String rootDecisionInstanceId;
  private String decisionRequirementsDefinitionId;
  private String decisionRequirementsDefinitionKey;
  private String tenantId;

  @Override
  public Optional<String> getTenantId() {
    return Optional.ofNullable(tenantId);
  }
}
