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
      final String id,
      final String name,
      final String type,
      final List<String> value,
      final OffsetDateTime timestamp,
      final Map<String, Object> valueInfo,
      final String processDefinitionKey,
      final String processDefinitionId,
      final String processInstanceId,
      final Long version,
      final String engineAlias,
      final String tenantId) {
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
    return id;
  }

  public ProcessVariableDto setId(final String id) {
    this.id = id;
    return this;
  }

  public String getName() {
    return name;
  }

  public ProcessVariableDto setName(final String name) {
    this.name = name;
    return this;
  }

  public String getType() {
    return type;
  }

  public ProcessVariableDto setType(final String type) {
    this.type = type;
    return this;
  }

  public List<String> getValue() {
    return value;
  }

  public ProcessVariableDto setValue(final List<String> value) {
    this.value = value;
    return this;
  }

  public OffsetDateTime getTimestamp() {
    return timestamp;
  }

  public ProcessVariableDto setTimestamp(final OffsetDateTime timestamp) {
    this.timestamp = timestamp;
    return this;
  }

  public Map<String, Object> getValueInfo() {
    return valueInfo;
  }

  public ProcessVariableDto setValueInfo(final Map<String, Object> valueInfo) {
    this.valueInfo = valueInfo;
    return this;
  }

  public String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public ProcessVariableDto setProcessDefinitionKey(final String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public ProcessVariableDto setProcessDefinitionId(final String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
    return this;
  }

  public String getProcessInstanceId() {
    return processInstanceId;
  }

  public ProcessVariableDto setProcessInstanceId(final String processInstanceId) {
    this.processInstanceId = processInstanceId;
    return this;
  }

  public Long getVersion() {
    return version;
  }

  public ProcessVariableDto setVersion(final Long version) {
    this.version = version;
    return this;
  }

  public String getEngineAlias() {
    return engineAlias;
  }

  public ProcessVariableDto setEngineAlias(final String engineAlias) {
    this.engineAlias = engineAlias;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public ProcessVariableDto setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ProcessVariableDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $id = getId();
    result = result * PRIME + ($id == null ? 43 : $id.hashCode());
    final Object $name = getName();
    result = result * PRIME + ($name == null ? 43 : $name.hashCode());
    final Object $type = getType();
    result = result * PRIME + ($type == null ? 43 : $type.hashCode());
    final Object $value = getValue();
    result = result * PRIME + ($value == null ? 43 : $value.hashCode());
    final Object $timestamp = getTimestamp();
    result = result * PRIME + ($timestamp == null ? 43 : $timestamp.hashCode());
    final Object $valueInfo = getValueInfo();
    result = result * PRIME + ($valueInfo == null ? 43 : $valueInfo.hashCode());
    final Object $processDefinitionKey = getProcessDefinitionKey();
    result =
        result * PRIME + ($processDefinitionKey == null ? 43 : $processDefinitionKey.hashCode());
    final Object $processDefinitionId = getProcessDefinitionId();
    result = result * PRIME + ($processDefinitionId == null ? 43 : $processDefinitionId.hashCode());
    final Object $processInstanceId = getProcessInstanceId();
    result = result * PRIME + ($processInstanceId == null ? 43 : $processInstanceId.hashCode());
    final Object $version = getVersion();
    result = result * PRIME + ($version == null ? 43 : $version.hashCode());
    final Object $engineAlias = getEngineAlias();
    result = result * PRIME + ($engineAlias == null ? 43 : $engineAlias.hashCode());
    final Object $tenantId = getTenantId();
    result = result * PRIME + ($tenantId == null ? 43 : $tenantId.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof ProcessVariableDto)) {
      return false;
    }
    final ProcessVariableDto other = (ProcessVariableDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$id = getId();
    final Object other$id = other.getId();
    if (this$id == null ? other$id != null : !this$id.equals(other$id)) {
      return false;
    }
    final Object this$name = getName();
    final Object other$name = other.getName();
    if (this$name == null ? other$name != null : !this$name.equals(other$name)) {
      return false;
    }
    final Object this$type = getType();
    final Object other$type = other.getType();
    if (this$type == null ? other$type != null : !this$type.equals(other$type)) {
      return false;
    }
    final Object this$value = getValue();
    final Object other$value = other.getValue();
    if (this$value == null ? other$value != null : !this$value.equals(other$value)) {
      return false;
    }
    final Object this$timestamp = getTimestamp();
    final Object other$timestamp = other.getTimestamp();
    if (this$timestamp == null
        ? other$timestamp != null
        : !this$timestamp.equals(other$timestamp)) {
      return false;
    }
    final Object this$valueInfo = getValueInfo();
    final Object other$valueInfo = other.getValueInfo();
    if (this$valueInfo == null
        ? other$valueInfo != null
        : !this$valueInfo.equals(other$valueInfo)) {
      return false;
    }
    final Object this$processDefinitionKey = getProcessDefinitionKey();
    final Object other$processDefinitionKey = other.getProcessDefinitionKey();
    if (this$processDefinitionKey == null
        ? other$processDefinitionKey != null
        : !this$processDefinitionKey.equals(other$processDefinitionKey)) {
      return false;
    }
    final Object this$processDefinitionId = getProcessDefinitionId();
    final Object other$processDefinitionId = other.getProcessDefinitionId();
    if (this$processDefinitionId == null
        ? other$processDefinitionId != null
        : !this$processDefinitionId.equals(other$processDefinitionId)) {
      return false;
    }
    final Object this$processInstanceId = getProcessInstanceId();
    final Object other$processInstanceId = other.getProcessInstanceId();
    if (this$processInstanceId == null
        ? other$processInstanceId != null
        : !this$processInstanceId.equals(other$processInstanceId)) {
      return false;
    }
    final Object this$version = getVersion();
    final Object other$version = other.getVersion();
    if (this$version == null ? other$version != null : !this$version.equals(other$version)) {
      return false;
    }
    final Object this$engineAlias = getEngineAlias();
    final Object other$engineAlias = other.getEngineAlias();
    if (this$engineAlias == null
        ? other$engineAlias != null
        : !this$engineAlias.equals(other$engineAlias)) {
      return false;
    }
    final Object this$tenantId = getTenantId();
    final Object other$tenantId = other.getTenantId();
    if (this$tenantId == null ? other$tenantId != null : !this$tenantId.equals(other$tenantId)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "ProcessVariableDto(id="
        + getId()
        + ", name="
        + getName()
        + ", type="
        + getType()
        + ", value="
        + getValue()
        + ", timestamp="
        + getTimestamp()
        + ", valueInfo="
        + getValueInfo()
        + ", processDefinitionKey="
        + getProcessDefinitionKey()
        + ", processDefinitionId="
        + getProcessDefinitionId()
        + ", processInstanceId="
        + getProcessInstanceId()
        + ", version="
        + getVersion()
        + ", engineAlias="
        + getEngineAlias()
        + ", tenantId="
        + getTenantId()
        + ")";
  }
}
