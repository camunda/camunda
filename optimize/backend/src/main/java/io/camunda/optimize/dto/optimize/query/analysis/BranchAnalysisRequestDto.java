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
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
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
