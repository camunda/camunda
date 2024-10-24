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
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
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

  private static List<String> defaultProcessDefinitionVersions() {
    return new ArrayList<>();
  }

  private static List<String> defaultTenantIds() {
    return new ArrayList<>(Collections.singletonList(null));
  }

  public static ProcessVariableSourceDtoBuilder builder() {
    return new ProcessVariableSourceDtoBuilder();
  }

  public static class ProcessVariableSourceDtoBuilder {

    private String processInstanceId;
    private String processDefinitionKey;
    private List<String> processDefinitionVersionsValue;
    private boolean processDefinitionVersionsSet;
    private List<String> tenantIdsValue;
    private boolean tenantIdsSet;

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
      processDefinitionVersionsValue = processDefinitionVersions;
      processDefinitionVersionsSet = true;
      return this;
    }

    public ProcessVariableSourceDtoBuilder tenantIds(final List<String> tenantIds) {
      tenantIdsValue = tenantIds;
      tenantIdsSet = true;
      return this;
    }

    public ProcessVariableSourceDto build() {
      List<String> processDefinitionVersionsValue = this.processDefinitionVersionsValue;
      if (!processDefinitionVersionsSet) {
        processDefinitionVersionsValue =
            ProcessVariableSourceDto.defaultProcessDefinitionVersions();
      }
      List<String> tenantIdsValue = this.tenantIdsValue;
      if (!tenantIdsSet) {
        tenantIdsValue = ProcessVariableSourceDto.defaultTenantIds();
      }
      return new ProcessVariableSourceDto(
          processInstanceId, processDefinitionKey, processDefinitionVersionsValue, tenantIdsValue);
    }

    @Override
    public String toString() {
      return "ProcessVariableSourceDto.ProcessVariableSourceDtoBuilder(processInstanceId="
          + processInstanceId
          + ", processDefinitionKey="
          + processDefinitionKey
          + ", processDefinitionVersionsValue="
          + processDefinitionVersionsValue
          + ", tenantIdsValue="
          + tenantIdsValue
          + ")";
    }
  }
}
