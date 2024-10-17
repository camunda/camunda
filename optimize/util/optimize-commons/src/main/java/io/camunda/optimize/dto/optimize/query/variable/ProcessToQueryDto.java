/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.variable;

import static io.camunda.optimize.dto.optimize.ReportConstants.DEFAULT_TENANT_IDS;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Lists;
import io.camunda.optimize.service.util.TenantListHandlingUtil;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

public class ProcessToQueryDto {

  @NotNull private String processDefinitionKey;
  private List<String> processDefinitionVersions = new ArrayList<>();
  private List<String> tenantIds = new ArrayList<>(DEFAULT_TENANT_IDS);

  public ProcessToQueryDto(
      @NotNull final String processDefinitionKey,
      final List<String> processDefinitionVersions,
      final List<String> tenantIds) {
    this.processDefinitionKey = processDefinitionKey;
    this.processDefinitionVersions = processDefinitionVersions;
    this.tenantIds = tenantIds;
  }

  public ProcessToQueryDto() {}

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

  public @NotNull String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public void setProcessDefinitionKey(@NotNull final String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public List<String> getProcessDefinitionVersions() {
    return processDefinitionVersions;
  }

  public void setProcessDefinitionVersions(final List<String> processDefinitionVersions) {
    this.processDefinitionVersions = processDefinitionVersions;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ProcessToQueryDto;
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
    return "ProcessToQueryDto(processDefinitionKey="
        + getProcessDefinitionKey()
        + ", processDefinitionVersions="
        + getProcessDefinitionVersions()
        + ", tenantIds="
        + getTenantIds()
        + ")";
  }
}
