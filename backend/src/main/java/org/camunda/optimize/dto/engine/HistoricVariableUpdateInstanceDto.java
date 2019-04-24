/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.engine;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;

@Data
public class HistoricVariableUpdateInstanceDto implements EngineDto {
  private String id;
  private String variableInstanceId;
  private String variableName;
  private String variableType;
  private String value;
  private Map<String, Object> valueInfo;
  private OffsetDateTime time;
  private long revision;
  private long sequenceCounter;

  private String processDefinitionKey;
  private String processDefinitionId;
  private String processInstanceId;

  private String tenantId;
}
