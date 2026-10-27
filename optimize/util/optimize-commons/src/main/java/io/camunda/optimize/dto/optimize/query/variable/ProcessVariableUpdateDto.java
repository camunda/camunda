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
import java.util.Map;

public class ProcessVariableUpdateDto implements OptimizeDto {

  private String id;
  private String name;
  private String type;
  private String value;
  private OffsetDateTime timestamp;
  private Map<String, Object> valueInfo;
  private String processDefinitionKey;
  private String processDefinitionId;
  private String processInstanceId;
  private Long version;
  private String engineAlias;
  private String tenantId;

  public ProcessVariableUpdateDto() {}

  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public String getType() {
    return type;
  }

  public void setType(final String type) {
    this.type = type;
  }

  public String getValue() {
    return value;
  }

  public void setValue(final String value) {
    this.value = value;
  }

  public OffsetDateTime getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(final OffsetDateTime timestamp) {
    this.timestamp = timestamp;
  }

  public Map<String, Object> getValueInfo() {
    return valueInfo;
  }

  public void setValueInfo(final Map<String, Object> valueInfo) {
    this.valueInfo = valueInfo;
  }

  public String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public void setProcessDefinitionKey(final String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public void setProcessDefinitionId(final String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
  }

  public String getProcessInstanceId() {
    return processInstanceId;
  }

  public void setProcessInstanceId(final String processInstanceId) {
    this.processInstanceId = processInstanceId;
  }

  public Long getVersion() {
    return version;
  }

  public void setVersion(final Long version) {
    this.version = version;
  }

  public String getEngineAlias() {
    return engineAlias;
  }

  public void setEngineAlias(final String engineAlias) {
    this.engineAlias = engineAlias;
  }

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(final String tenantId) {
    this.tenantId = tenantId;
  }
}
