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

public class DecisionVariableValueRequestDto {

  private String decisionDefinitionKey;
  private List<String> decisionDefinitionVersions = new ArrayList<>();
  private List<String> tenantIds = new ArrayList<>(Collections.singletonList(null));
  private String variableId;
  private VariableType variableType;
  private String valueFilter;
  private Integer resultOffset = 0;
  private Integer numResults = MAX_RESPONSE_SIZE_LIMIT;

  public DecisionVariableValueRequestDto() {}

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

  public String getDecisionDefinitionKey() {
    return decisionDefinitionKey;
  }

  public void setDecisionDefinitionKey(final String decisionDefinitionKey) {
    this.decisionDefinitionKey = decisionDefinitionKey;
  }

  public List<String> getDecisionDefinitionVersions() {
    return decisionDefinitionVersions;
  }

  public void setDecisionDefinitionVersions(final List<String> decisionDefinitionVersions) {
    this.decisionDefinitionVersions = decisionDefinitionVersions;
  }

  public String getVariableId() {
    return variableId;
  }

  public void setVariableId(final String variableId) {
    this.variableId = variableId;
  }

  public VariableType getVariableType() {
    return variableType;
  }

  public void setVariableType(final VariableType variableType) {
    this.variableType = variableType;
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
    return other instanceof DecisionVariableValueRequestDto;
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
    final Object $variableId = getVariableId();
    result = result * PRIME + ($variableId == null ? 43 : $variableId.hashCode());
    final Object $variableType = getVariableType();
    result = result * PRIME + ($variableType == null ? 43 : $variableType.hashCode());
    final Object $valueFilter = getValueFilter();
    result = result * PRIME + ($valueFilter == null ? 43 : $valueFilter.hashCode());
    final Object $resultOffset = getResultOffset();
    result = result * PRIME + ($resultOffset == null ? 43 : $resultOffset.hashCode());
    final Object $numResults = getNumResults();
    result = result * PRIME + ($numResults == null ? 43 : $numResults.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof DecisionVariableValueRequestDto)) {
      return false;
    }
    final DecisionVariableValueRequestDto other = (DecisionVariableValueRequestDto) o;
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
    final Object this$variableId = getVariableId();
    final Object other$variableId = other.getVariableId();
    if (this$variableId == null
        ? other$variableId != null
        : !this$variableId.equals(other$variableId)) {
      return false;
    }
    final Object this$variableType = getVariableType();
    final Object other$variableType = other.getVariableType();
    if (this$variableType == null
        ? other$variableType != null
        : !this$variableType.equals(other$variableType)) {
      return false;
    }
    final Object this$valueFilter = getValueFilter();
    final Object other$valueFilter = other.getValueFilter();
    if (this$valueFilter == null
        ? other$valueFilter != null
        : !this$valueFilter.equals(other$valueFilter)) {
      return false;
    }
    final Object this$resultOffset = getResultOffset();
    final Object other$resultOffset = other.getResultOffset();
    if (this$resultOffset == null
        ? other$resultOffset != null
        : !this$resultOffset.equals(other$resultOffset)) {
      return false;
    }
    final Object this$numResults = getNumResults();
    final Object other$numResults = other.getNumResults();
    if (this$numResults == null
        ? other$numResults != null
        : !this$numResults.equals(other$numResults)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "DecisionVariableValueRequestDto(decisionDefinitionKey="
        + getDecisionDefinitionKey()
        + ", decisionDefinitionVersions="
        + getDecisionDefinitionVersions()
        + ", tenantIds="
        + getTenantIds()
        + ", variableId="
        + getVariableId()
        + ", variableType="
        + getVariableType()
        + ", valueFilter="
        + getValueFilter()
        + ", resultOffset="
        + getResultOffset()
        + ", numResults="
        + getNumResults()
        + ")";
  }
}
