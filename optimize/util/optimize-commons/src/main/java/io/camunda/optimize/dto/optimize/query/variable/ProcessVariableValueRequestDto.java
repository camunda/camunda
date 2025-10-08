/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.variable;

import static io.camunda.optimize.service.db.DatabaseConstants.MAX_RESPONSE_SIZE_LIMIT;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Lists;
import io.camunda.optimize.service.util.TenantListHandlingUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class ProcessVariableValueRequestDto {

  private String processInstanceId;
  private String processDefinitionKey;
  private List<String> processDefinitionVersions = new ArrayList<>();
  private List<String> tenantIds = new ArrayList<>(Collections.singletonList(null));
  private String name;
  private VariableType type;
  private String valueFilter;
  private Integer resultOffset = 0;
  private Integer numResults = MAX_RESPONSE_SIZE_LIMIT;

  public ProcessVariableValueRequestDto() {}

  @JsonIgnore
  public void setProcessDefinitionVersion(final String processDefinitionVersion) {
    processDefinitionVersions = Lists.newArrayList(processDefinitionVersion);
  }

  public List<String> getTenantIds() {
    return TenantListHandlingUtil.sortAndReturnTenantIdList(tenantIds);
  }

  public void setTenantIds(final List<String> tenantIds) {
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

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public VariableType getType() {
    return type;
  }

  public void setType(final VariableType type) {
    this.type = type;
  }

  public String getValueFilter() {
    return valueFilter;
  }

  public void setValueFilter(final String valueFilter) {
    this.valueFilter = valueFilter;
  }

  public Integer getResultOffset() {
    return resultOffset;
  }

  public void setResultOffset(final Integer resultOffset) {
    this.resultOffset = resultOffset;
  }

  public Integer getNumResults() {
    return numResults;
  }

  public void setNumResults(final Integer numResults) {
    this.numResults = numResults;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ProcessVariableValueRequestDto;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        processInstanceId,
        processDefinitionKey,
        processDefinitionVersions,
        tenantIds,
        name,
        type,
        valueFilter,
        resultOffset,
        numResults);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ProcessVariableValueRequestDto that = (ProcessVariableValueRequestDto) o;
    return Objects.equals(processInstanceId, that.processInstanceId)
        && Objects.equals(processDefinitionKey, that.processDefinitionKey)
        && Objects.equals(processDefinitionVersions, that.processDefinitionVersions)
        && Objects.equals(tenantIds, that.tenantIds)
        && Objects.equals(name, that.name)
        && Objects.equals(type, that.type)
        && Objects.equals(valueFilter, that.valueFilter)
        && Objects.equals(resultOffset, that.resultOffset)
        && Objects.equals(numResults, that.numResults);
  }

  @Override
  public String toString() {
    return "ProcessVariableValueRequestDto(processInstanceId="
        + getProcessInstanceId()
        + ", processDefinitionKey="
        + getProcessDefinitionKey()
        + ", processDefinitionVersions="
        + getProcessDefinitionVersions()
        + ", tenantIds="
        + getTenantIds()
        + ", name="
        + getName()
        + ", type="
        + getType()
        + ", valueFilter="
        + getValueFilter()
        + ", resultOffset="
        + getResultOffset()
        + ", numResults="
        + getNumResults()
        + ")";
  }
}
