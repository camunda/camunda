/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.analysis;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Lists;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import io.camunda.optimize.service.util.TenantListHandlingUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BranchAnalysisRequestDto {

  private String end;
  private String gateway;
  private String processDefinitionKey;
  private List<String> processDefinitionVersions;
  private List<String> tenantIds = Collections.singletonList(null);

  private List<ProcessFilterDto<?>> filter = new ArrayList<>();

  public BranchAnalysisRequestDto() {}

  @JsonIgnore
  public void setProcessDefinitionVersion(final String definitionVersion) {
    processDefinitionVersions = Lists.newArrayList(definitionVersion);
  }

  public List<String> getTenantIds() {
    return TenantListHandlingUtil.sortAndReturnTenantIdList(tenantIds);
  }

  public void setTenantIds(final List<String> tenantIds) {
    this.tenantIds = tenantIds;
  }

  public String getEnd() {
    return end;
  }

  public void setEnd(final String end) {
    this.end = end;
  }

  public String getGateway() {
    return gateway;
  }

  public void setGateway(final String gateway) {
    this.gateway = gateway;
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

  public List<ProcessFilterDto<?>> getFilter() {
    return filter;
  }

  public void setFilter(final List<ProcessFilterDto<?>> filter) {
    this.filter = filter;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof BranchAnalysisRequestDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $end = getEnd();
    result = result * PRIME + ($end == null ? 43 : $end.hashCode());
    final Object $gateway = getGateway();
    result = result * PRIME + ($gateway == null ? 43 : $gateway.hashCode());
    final Object $processDefinitionKey = getProcessDefinitionKey();
    result =
        result * PRIME + ($processDefinitionKey == null ? 43 : $processDefinitionKey.hashCode());
    final Object $processDefinitionVersions = getProcessDefinitionVersions();
    result =
        result * PRIME
            + ($processDefinitionVersions == null ? 43 : $processDefinitionVersions.hashCode());
    final Object $tenantIds = getTenantIds();
    result = result * PRIME + ($tenantIds == null ? 43 : $tenantIds.hashCode());
    final Object $filter = getFilter();
    result = result * PRIME + ($filter == null ? 43 : $filter.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof BranchAnalysisRequestDto)) {
      return false;
    }
    final BranchAnalysisRequestDto other = (BranchAnalysisRequestDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$end = getEnd();
    final Object other$end = other.getEnd();
    if (this$end == null ? other$end != null : !this$end.equals(other$end)) {
      return false;
    }
    final Object this$gateway = getGateway();
    final Object other$gateway = other.getGateway();
    if (this$gateway == null ? other$gateway != null : !this$gateway.equals(other$gateway)) {
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
    final Object this$filter = getFilter();
    final Object other$filter = other.getFilter();
    if (this$filter == null ? other$filter != null : !this$filter.equals(other$filter)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "BranchAnalysisRequestDto(end="
        + getEnd()
        + ", gateway="
        + getGateway()
        + ", processDefinitionKey="
        + getProcessDefinitionKey()
        + ", processDefinitionVersions="
        + getProcessDefinitionVersions()
        + ", tenantIds="
        + getTenantIds()
        + ", filter="
        + getFilter()
        + ")";
  }
}
