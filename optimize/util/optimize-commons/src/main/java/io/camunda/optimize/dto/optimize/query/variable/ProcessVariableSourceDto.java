/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.variable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ProcessVariableSourceDto {

  private String processInstanceId;
  private String processDefinitionKey;
  private List<String> processDefinitionVersions = new ArrayList<>();

  private List<String> tenantIds = new ArrayList<>(Collections.singletonList(null));

  ProcessVariableSourceDto(
      final String processInstanceId,
      final String processDefinitionKey,
      final List<String> processDefinitionVersions,
      final List<String> tenantIds) {
    this.processInstanceId = processInstanceId;
    this.processDefinitionKey = processDefinitionKey;
    this.processDefinitionVersions = processDefinitionVersions;
    this.tenantIds = tenantIds;
  }

  public String getProcessInstanceId() {
    return processInstanceId;
  }

  public void setProcessInstanceId(final String processInstanceId) {
    this.processInstanceId = processInstanceId;
  }

  public String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public void setProcessDefinitionKey(final String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public List<String> getProcessDefinitionVersions() {
    return processDefinitionVersions;
  }

  public void setProcessDefinitionVersions(final List<String> processDefinitionVersions) {
    this.processDefinitionVersions = processDefinitionVersions;
  }

  public List<String> getTenantIds() {
    return tenantIds;
  }

  public void setTenantIds(final List<String> tenantIds) {
    this.tenantIds = tenantIds;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ProcessVariableSourceDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $processInstanceId = getProcessInstanceId();
    result = result * PRIME + ($processInstanceId == null ? 43 : $processInstanceId.hashCode());
    final Object $processDefinitionKey = getProcessDefinitionKey();
    result =
        result * PRIME + ($processDefinitionKey == null ? 43 : $processDefinitionKey.hashCode());
    final Object $processDefinitionVersions = getProcessDefinitionVersions();
    result =
        result * PRIME
            + ($processDefinitionVersions == null ? 43 : $processDefinitionVersions.hashCode());
    final Object $tenantIds = getTenantIds();
    result = result * PRIME + ($tenantIds == null ? 43 : $tenantIds.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof ProcessVariableSourceDto)) {
      return false;
    }
    final ProcessVariableSourceDto other = (ProcessVariableSourceDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$processInstanceId = getProcessInstanceId();
    final Object other$processInstanceId = other.getProcessInstanceId();
    if (this$processInstanceId == null
        ? other$processInstanceId != null
        : !this$processInstanceId.equals(other$processInstanceId)) {
      return false;
    }
    final Object this$processDefinitionKey = getProcessDefinitionKey();
    final Object other$processDefinitionKey = other.getProcessDefinitionKey();
    if (this$processDefinitionKey == null
        ? other$processDefinitionKey != null
        : !this$processDefinitionKey.equals(other$processDefinitionKey)) {
      return false;
    }
    final Object this$processDefinitionVersions = getProcessDefinitionVersions();
    final Object other$processDefinitionVersions = other.getProcessDefinitionVersions();
    if (this$processDefinitionVersions == null
        ? other$processDefinitionVersions != null
        : !this$processDefinitionVersions.equals(other$processDefinitionVersions)) {
      return false;
    }
    final Object this$tenantIds = getTenantIds();
    final Object other$tenantIds = other.getTenantIds();
    if (this$tenantIds == null
        ? other$tenantIds != null
        : !this$tenantIds.equals(other$tenantIds)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "ProcessVariableSourceDto(processInstanceId="
        + getProcessInstanceId()
        + ", processDefinitionKey="
        + getProcessDefinitionKey()
        + ", processDefinitionVersions="
        + getProcessDefinitionVersions()
        + ", tenantIds="
        + getTenantIds()
        + ")";
  }

  private static List<String> $default$processDefinitionVersions() {
    return new ArrayList<>();
  }

  private static List<String> $default$tenantIds() {
    return new ArrayList<>(Collections.singletonList(null));
  }

  public static ProcessVariableSourceDtoBuilder builder() {
    return new ProcessVariableSourceDtoBuilder();
  }

  public static class ProcessVariableSourceDtoBuilder {

    private String processInstanceId;
    private String processDefinitionKey;
    private List<String> processDefinitionVersions$value;
    private boolean processDefinitionVersions$set;
    private List<String> tenantIds$value;
    private boolean tenantIds$set;

    ProcessVariableSourceDtoBuilder() {}

    public ProcessVariableSourceDtoBuilder processInstanceId(final String processInstanceId) {
      this.processInstanceId = processInstanceId;
      return this;
    }

    public ProcessVariableSourceDtoBuilder processDefinitionKey(final String processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return this;
    }

    public ProcessVariableSourceDtoBuilder processDefinitionVersions(
        final List<String> processDefinitionVersions) {
      processDefinitionVersions$value = processDefinitionVersions;
      processDefinitionVersions$set = true;
      return this;
    }

    public ProcessVariableSourceDtoBuilder tenantIds(final List<String> tenantIds) {
      tenantIds$value = tenantIds;
      tenantIds$set = true;
      return this;
    }

    public ProcessVariableSourceDto build() {
      List<String> processDefinitionVersions$value = this.processDefinitionVersions$value;
      if (!processDefinitionVersions$set) {
        processDefinitionVersions$value =
            ProcessVariableSourceDto.$default$processDefinitionVersions();
      }
      List<String> tenantIds$value = this.tenantIds$value;
      if (!tenantIds$set) {
        tenantIds$value = ProcessVariableSourceDto.$default$tenantIds();
      }
      return new ProcessVariableSourceDto(
          processInstanceId,
          processDefinitionKey,
          processDefinitionVersions$value,
          tenantIds$value);
    }

    @Override
    public String toString() {
      return "ProcessVariableSourceDto.ProcessVariableSourceDtoBuilder(processInstanceId="
          + processInstanceId
          + ", processDefinitionKey="
          + processDefinitionKey
          + ", processDefinitionVersions$value="
          + processDefinitionVersions$value
          + ", tenantIds$value="
          + tenantIds$value
          + ")";
    }
  }
}
