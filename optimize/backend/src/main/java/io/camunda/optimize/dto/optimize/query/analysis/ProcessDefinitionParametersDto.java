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
    final int PRIME = 59;
    int result = 1;
    final Object $processDefinitionKey = getProcessDefinitionKey();
    result =
        result * PRIME + ($processDefinitionKey == null ? 43 : $processDefinitionKey.hashCode());
    final Object $processDefinitionVersions = getProcessDefinitionVersions();
    result =
        result * PRIME
            + ($processDefinitionVersions == null ? 43 : $processDefinitionVersions.hashCode());
    final Object $tenantIds = getTenantIds();
    result = result * PRIME + ($tenantIds == null ? 43 : $tenantIds.hashCode());
    final Object $minimumDeviationFromAvg = getMinimumDeviationFromAvg();
    result =
        result * PRIME
            + ($minimumDeviationFromAvg == null ? 43 : $minimumDeviationFromAvg.hashCode());
    final Object $disconsiderAutomatedTasks = getDisconsiderAutomatedTasks();
    result =
        result * PRIME
            + ($disconsiderAutomatedTasks == null ? 43 : $disconsiderAutomatedTasks.hashCode());
    final Object $filters = getFilters();
    result = result * PRIME + ($filters == null ? 43 : $filters.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof ProcessDefinitionParametersDto)) {
      return false;
    }
    final ProcessDefinitionParametersDto other = (ProcessDefinitionParametersDto) o;
    if (!other.canEqual((Object) this)) {
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
    final Object this$minimumDeviationFromAvg = getMinimumDeviationFromAvg();
    final Object other$minimumDeviationFromAvg = other.getMinimumDeviationFromAvg();
    if (this$minimumDeviationFromAvg == null
        ? other$minimumDeviationFromAvg != null
        : !this$minimumDeviationFromAvg.equals(other$minimumDeviationFromAvg)) {
      return false;
    }
    final Object this$disconsiderAutomatedTasks = getDisconsiderAutomatedTasks();
    final Object other$disconsiderAutomatedTasks = other.getDisconsiderAutomatedTasks();
    if (this$disconsiderAutomatedTasks == null
        ? other$disconsiderAutomatedTasks != null
        : !this$disconsiderAutomatedTasks.equals(other$disconsiderAutomatedTasks)) {
      return false;
    }
    final Object this$filters = getFilters();
    final Object other$filters = other.getFilters();
    if (this$filters == null ? other$filters != null : !this$filters.equals(other$filters)) {
      return false;
    }
    return true;
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
