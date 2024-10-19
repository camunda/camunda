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

  public ProcessVariableDto(
      String id,
      String name,
      String type,
      List<String> value,
      OffsetDateTime timestamp,
      Map<String, Object> valueInfo,
      String processDefinitionKey,
      String processDefinitionId,
      String processInstanceId,
      Long version,
      String engineAlias,
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

  public ProcessVariableDto() {}

  public String getId() {
    return this.id;
  }

  public String getName() {
    return this.name;
  }

  public String getType() {
    return this.type;
  }

  public List<String> getValue() {
    return this.value;
  }

  public OffsetDateTime getTimestamp() {
    return this.timestamp;
  }

  public Map<String, Object> getValueInfo() {
    return this.valueInfo;
  }

  public String getProcessDefinitionKey() {
    return this.processDefinitionKey;
  }

  public String getProcessDefinitionId() {
    return this.processDefinitionId;
  }

  public String getProcessInstanceId() {
    return this.processInstanceId;
  }

  public Long getVersion() {
    return this.version;
  }

  public String getEngineAlias() {
    return this.engineAlias;
  }

  public String getTenantId() {
    return this.tenantId;
  }

  public void setId(String id) {
    this.id = id;
  }

  public void setName(String name) {
    this.name = name;
  }

  public void setType(String type) {
    this.type = type;
  }

  public void setValue(List<String> value) {
    this.value = value;
  }

  public void setTimestamp(OffsetDateTime timestamp) {
    this.timestamp = timestamp;
  }

  public void setValueInfo(Map<String, Object> valueInfo) {
    this.valueInfo = valueInfo;
  }

  public void setProcessDefinitionKey(String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public void setProcessDefinitionId(String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
  }

  public void setProcessInstanceId(String processInstanceId) {
    this.processInstanceId = processInstanceId;
  }

  public void setVersion(Long version) {
    this.version = version;
  }

  public void setEngineAlias(String engineAlias) {
    this.engineAlias = engineAlias;
  }

  public void setTenantId(String tenantId) {
    this.tenantId = tenantId;
  }

  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ProcessVariableDto;
  }

  public int hashCode() {
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  public String toString() {
    return "ProcessVariableDto(id="
        + this.getId()
        + ", name="
        + this.getName()
        + ", type="
        + this.getType()
        + ", value="
        + this.getValue()
        + ", timestamp="
        + this.getTimestamp()
        + ", valueInfo="
        + this.getValueInfo()
        + ", processDefinitionKey="
        + this.getProcessDefinitionKey()
        + ", processDefinitionId="
        + this.getProcessDefinitionId()
        + ", processInstanceId="
        + this.getProcessInstanceId()
        + ", version="
        + this.getVersion()
        + ", engineAlias="
        + this.getEngineAlias()
        + ", tenantId="
        + this.getTenantId()
        + ")";
  }
}
