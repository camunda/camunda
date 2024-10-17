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
import java.util.Collections;
import java.util.List;

public class DecisionVariableNameRequestDto {

  @NotNull private String decisionDefinitionKey;
  private List<String> decisionDefinitionVersions = new ArrayList<>();
  private List<String> tenantIds = new ArrayList<>(DEFAULT_TENANT_IDS);

  public DecisionVariableNameRequestDto(
      @NotNull final String key, final String version, final String tenantId) {
    decisionDefinitionKey = key;
    decisionDefinitionVersions = Collections.singletonList(version);
    tenantIds = Collections.singletonList(tenantId);
  }

  public DecisionVariableNameRequestDto(@NotNull final String key, final List<String> versions) {
    decisionDefinitionKey = key;
    decisionDefinitionVersions = versions;
  }

  public DecisionVariableNameRequestDto(
      @NotNull final String decisionDefinitionKey,
      final List<String> decisionDefinitionVersions,
      final List<String> tenantIds) {
    this.decisionDefinitionKey = decisionDefinitionKey;
    this.decisionDefinitionVersions = decisionDefinitionVersions;
    this.tenantIds = tenantIds;
  }

  public DecisionVariableNameRequestDto() {}

  @JsonIgnore
  public void setDecisionDefinitionVersion(final String decisionDefinitionVersion) {
    decisionDefinitionVersions = Lists.newArrayList(decisionDefinitionVersion);
  }

  public List<String> getTenantIds() {
    return TenantListHandlingUtil.sortAndReturnTenantIdList(tenantIds);
  }

  public void setTenantIds(final List<String> tenantIds) {
    this.tenantIds = tenantIds;
  }

  public @NotNull String getDecisionDefinitionKey() {
    return decisionDefinitionKey;
  }

  public void setDecisionDefinitionKey(@NotNull final String decisionDefinitionKey) {
    this.decisionDefinitionKey = decisionDefinitionKey;
  }

  public List<String> getDecisionDefinitionVersions() {
    return decisionDefinitionVersions;
  }

  public void setDecisionDefinitionVersions(final List<String> decisionDefinitionVersions) {
    this.decisionDefinitionVersions = decisionDefinitionVersions;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof DecisionVariableNameRequestDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $decisionDefinitionKey = getDecisionDefinitionKey();
    result =
        result * PRIME + ($decisionDefinitionKey == null ? 43 : $decisionDefinitionKey.hashCode());
    final Object $decisionDefinitionVersions = getDecisionDefinitionVersions();
    result =
        result * PRIME
            + ($decisionDefinitionVersions == null ? 43 : $decisionDefinitionVersions.hashCode());
    final Object $tenantIds = getTenantIds();
    result = result * PRIME + ($tenantIds == null ? 43 : $tenantIds.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof DecisionVariableNameRequestDto)) {
      return false;
    }
    final DecisionVariableNameRequestDto other = (DecisionVariableNameRequestDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$decisionDefinitionKey = getDecisionDefinitionKey();
    final Object other$decisionDefinitionKey = other.getDecisionDefinitionKey();
    if (this$decisionDefinitionKey == null
        ? other$decisionDefinitionKey != null
        : !this$decisionDefinitionKey.equals(other$decisionDefinitionKey)) {
      return false;
    }
    final Object this$decisionDefinitionVersions = getDecisionDefinitionVersions();
    final Object other$decisionDefinitionVersions = other.getDecisionDefinitionVersions();
    if (this$decisionDefinitionVersions == null
        ? other$decisionDefinitionVersions != null
        : !this$decisionDefinitionVersions.equals(other$decisionDefinitionVersions)) {
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
    return "DecisionVariableNameRequestDto(decisionDefinitionKey="
        + getDecisionDefinitionKey()
        + ", decisionDefinitionVersions="
        + getDecisionDefinitionVersions()
        + ", tenantIds="
        + getTenantIds()
        + ")";
  }
}
