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
    final Object this$id = this.getId();
    final Object other$id = other.getId();
    if (this$id == null ? other$id != null : !this$id.equals(other$id)) {
      return false;
    }
    final Object this$name = this.getName();
    final Object other$name = other.getName();
    if (this$name == null ? other$name != null : !this$name.equals(other$name)) {
      return false;
    }
    final Object this$type = this.getType();
    final Object other$type = other.getType();
    if (this$type == null ? other$type != null : !this$type.equals(other$type)) {
      return false;
    }
    final Object this$value = this.getValue();
    final Object other$value = other.getValue();
    if (this$value == null ? other$value != null : !this$value.equals(other$value)) {
      return false;
    }
    final Object this$timestamp = this.getTimestamp();
    final Object other$timestamp = other.getTimestamp();
    if (this$timestamp == null
        ? other$timestamp != null
        : !this$timestamp.equals(other$timestamp)) {
      return false;
    }
    final Object this$valueInfo = this.getValueInfo();
    final Object other$valueInfo = other.getValueInfo();
    if (this$valueInfo == null
        ? other$valueInfo != null
        : !this$valueInfo.equals(other$valueInfo)) {
      return false;
    }
    final Object this$processDefinitionKey = this.getProcessDefinitionKey();
    final Object other$processDefinitionKey = other.getProcessDefinitionKey();
    if (this$processDefinitionKey == null
        ? other$processDefinitionKey != null
        : !this$processDefinitionKey.equals(other$processDefinitionKey)) {
      return false;
    }
    final Object this$processDefinitionId = this.getProcessDefinitionId();
    final Object other$processDefinitionId = other.getProcessDefinitionId();
    if (this$processDefinitionId == null
        ? other$processDefinitionId != null
        : !this$processDefinitionId.equals(other$processDefinitionId)) {
      return false;
    }
    final Object this$processInstanceId = this.getProcessInstanceId();
    final Object other$processInstanceId = other.getProcessInstanceId();
    if (this$processInstanceId == null
        ? other$processInstanceId != null
        : !this$processInstanceId.equals(other$processInstanceId)) {
      return false;
    }
    final Object this$version = this.getVersion();
    final Object other$version = other.getVersion();
    if (this$version == null ? other$version != null : !this$version.equals(other$version)) {
      return false;
    }
    final Object this$engineAlias = this.getEngineAlias();
    final Object other$engineAlias = other.getEngineAlias();
    if (this$engineAlias == null
        ? other$engineAlias != null
        : !this$engineAlias.equals(other$engineAlias)) {
      return false;
    }
    final Object this$tenantId = this.getTenantId();
    final Object other$tenantId = other.getTenantId();
    if (this$tenantId == null ? other$tenantId != null : !this$tenantId.equals(other$tenantId)) {
      return false;
    }
    return true;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ProcessVariableDto;
  }

  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $id = this.getId();
    result = result * PRIME + ($id == null ? 43 : $id.hashCode());
    final Object $name = this.getName();
    result = result * PRIME + ($name == null ? 43 : $name.hashCode());
    final Object $type = this.getType();
    result = result * PRIME + ($type == null ? 43 : $type.hashCode());
    final Object $value = this.getValue();
    result = result * PRIME + ($value == null ? 43 : $value.hashCode());
    final Object $timestamp = this.getTimestamp();
    result = result * PRIME + ($timestamp == null ? 43 : $timestamp.hashCode());
    final Object $valueInfo = this.getValueInfo();
    result = result * PRIME + ($valueInfo == null ? 43 : $valueInfo.hashCode());
    final Object $processDefinitionKey = this.getProcessDefinitionKey();
    result =
        result * PRIME + ($processDefinitionKey == null ? 43 : $processDefinitionKey.hashCode());
    final Object $processDefinitionId = this.getProcessDefinitionId();
    result = result * PRIME + ($processDefinitionId == null ? 43 : $processDefinitionId.hashCode());
    final Object $processInstanceId = this.getProcessInstanceId();
    result = result * PRIME + ($processInstanceId == null ? 43 : $processInstanceId.hashCode());
    final Object $version = this.getVersion();
    result = result * PRIME + ($version == null ? 43 : $version.hashCode());
    final Object $engineAlias = this.getEngineAlias();
    result = result * PRIME + ($engineAlias == null ? 43 : $engineAlias.hashCode());
    final Object $tenantId = this.getTenantId();
    result = result * PRIME + ($tenantId == null ? 43 : $tenantId.hashCode());
    return result;
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
