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

public class VariableUpdateInstanceDto implements OptimizeDto {

  private String instanceId;
  private String name;
  private String type;
  private List<String> value;
  private String processInstanceId;
  private String tenantId;
  private OffsetDateTime timestamp;

  public VariableUpdateInstanceDto(
      final String instanceId,
      final String name,
      final String type,
      final List<String> value,
      final String processInstanceId,
      final String tenantId,
      final OffsetDateTime timestamp) {
    this.instanceId = instanceId;
    this.name = name;
    this.type = type;
    this.value = value;
    this.processInstanceId = processInstanceId;
    this.tenantId = tenantId;
    this.timestamp = timestamp;
  }

  public VariableUpdateInstanceDto() {}

  public String getInstanceId() {
    return instanceId;
  }

  public void setInstanceId(final String instanceId) {
    this.instanceId = instanceId;
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

  public List<String> getValue() {
    return value;
  }

  public void setValue(final List<String> value) {
    this.value = value;
  }

  public String getProcessInstanceId() {
    return processInstanceId;
  }

  public void setProcessInstanceId(final String processInstanceId) {
    this.processInstanceId = processInstanceId;
  }

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(final String tenantId) {
    this.tenantId = tenantId;
  }

  public OffsetDateTime getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(final OffsetDateTime timestamp) {
    this.timestamp = timestamp;
  }

  public static VariableUpdateInstanceDtoBuilder builder() {
    return new VariableUpdateInstanceDtoBuilder();
  }

  public static final class Fields {

    public static final String instanceId = "instanceId";
    public static final String name = "name";
    public static final String type = "type";
    public static final String value = "value";
    public static final String processInstanceId = "processInstanceId";
    public static final String tenantId = "tenantId";
    public static final String timestamp = "timestamp";
  }

  public static class VariableUpdateInstanceDtoBuilder {

    private String instanceId;
    private String name;
    private String type;
    private List<String> value;
    private String processInstanceId;
    private String tenantId;
    private OffsetDateTime timestamp;

    VariableUpdateInstanceDtoBuilder() {}

    public VariableUpdateInstanceDtoBuilder instanceId(final String instanceId) {
      this.instanceId = instanceId;
      return this;
    }

    public VariableUpdateInstanceDtoBuilder name(final String name) {
      this.name = name;
      return this;
    }

    public VariableUpdateInstanceDtoBuilder type(final String type) {
      this.type = type;
      return this;
    }

    public VariableUpdateInstanceDtoBuilder value(final List<String> value) {
      this.value = value;
      return this;
    }

    public VariableUpdateInstanceDtoBuilder processInstanceId(final String processInstanceId) {
      this.processInstanceId = processInstanceId;
      return this;
    }

    public VariableUpdateInstanceDtoBuilder tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    public VariableUpdateInstanceDtoBuilder timestamp(final OffsetDateTime timestamp) {
      this.timestamp = timestamp;
      return this;
    }

    public VariableUpdateInstanceDto build() {
      return new VariableUpdateInstanceDto(
          instanceId, name, type, value, processInstanceId, tenantId, timestamp);
    }

    @Override
    public String toString() {
      return "VariableUpdateInstanceDto.VariableUpdateInstanceDtoBuilder(instanceId="
          + instanceId
          + ", name="
          + name
          + ", type="
          + type
          + ", value="
          + value
          + ", processInstanceId="
          + processInstanceId
          + ", tenantId="
          + tenantId
          + ", timestamp="
          + timestamp
          + ")";
    }
  }
}
