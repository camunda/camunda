/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.variable;

import io.camunda.optimize.dto.optimize.OptimizeDto;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class ProcessVariableDto implements OptimizeDto {

  private String id;
  private String name;
  private String type;
  private List<String> value;
  private OffsetDateTime timestamp;
  private Map<String, Object> valueInfo;
  private String processDefinitionKey;
  private String processDefinitionId;
  private String processInstanceId;
  private Long version;
  private String engineAlias;
  private String tenantId;

  public ProcessVariableDto(String id, String name, String type, List<String> value,
      OffsetDateTime timestamp, Map<String, Object> valueInfo, String processDefinitionKey,
      String processDefinitionId, String processInstanceId, Long version, String engineAlias,
      String tenantId) {
    this.id = id;
    this.name = name;
    this.type = type;
    this.value = value;
    this.timestamp = timestamp;
    this.valueInfo = valueInfo;
    this.processDefinitionKey = processDefinitionKey;
    this.processDefinitionId = processDefinitionId;
    this.processInstanceId = processInstanceId;
    this.version = version;
    this.engineAlias = engineAlias;
    this.tenantId = tenantId;
  }

  public ProcessVariableDto() {
  }
}