/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.analysis;

import static io.camunda.optimize.dto.optimize.ReportConstants.DEFAULT_TENANT_IDS;

import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import io.camunda.optimize.rest.queryparam.QueryParamUtil;
import io.camunda.optimize.service.util.TenantListHandlingUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ProcessDefinitionParametersDto {

  protected String processDefinitionKey;
  protected List<String> processDefinitionVersions;
  protected List<String> tenantIds = DEFAULT_TENANT_IDS;
  protected Long minimumDeviationFromAvg = 50L;
  protected Boolean disconsiderAutomatedTasks = false;
  protected List<ProcessFilterDto<?>> filters = new ArrayList<>();

  public ProcessDefinitionParametersDto() {}

  protected List<String> normalizeNullTenants(final List<String> tenantIds) {
    return tenantIds.stream()
        .map(QueryParamUtil::normalizeNullStringValue)
        .collect(Collectors.toList());
  }

  private List<String> normalizeTenants(final List<String> tenantIds) {
    final List<String> normalizedTenantIds = normalizeNullTenants(tenantIds);
    return normalizedTenantIds.isEmpty() ? DEFAULT_TENANT_IDS : normalizedTenantIds;
  }

  public List<String> getTenantIds() {
    return TenantListHandlingUtil.sortAndReturnTenantIdList(tenantIds);
  }

  public void setTenantIds(final List<String> tenantIds) {
    this.tenantIds = normalizeTenants(tenantIds);
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

  public Long getMinimumDeviationFromAvg() {
    return minimumDeviationFromAvg;
  }

  public void setMinimumDeviationFromAvg(final Long minimumDeviationFromAvg) {
    this.minimumDeviationFromAvg = minimumDeviationFromAvg;
  }

  public Boolean getDisconsiderAutomatedTasks() {
    return disconsiderAutomatedTasks;
  }

  public void setDisconsiderAutomatedTasks(final Boolean disconsiderAutomatedTasks) {
    this.disconsiderAutomatedTasks = disconsiderAutomatedTasks;
  }

  public List<ProcessFilterDto<?>> getFilters() {
    return filters;
  }

  public void setFilters(final List<ProcessFilterDto<?>> filters) {
    this.filters = filters;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ProcessDefinitionParametersDto;
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
    return "ProcessDefinitionParametersDto(processDefinitionKey="
        + getProcessDefinitionKey()
        + ", processDefinitionVersions="
        + getProcessDefinitionVersions()
        + ", tenantIds="
        + getTenantIds()
        + ", minimumDeviationFromAvg="
        + getMinimumDeviationFromAvg()
        + ", disconsiderAutomatedTasks="
        + getDisconsiderAutomatedTasks()
        + ", filters="
        + getFilters()
        + ")";
  }
}
