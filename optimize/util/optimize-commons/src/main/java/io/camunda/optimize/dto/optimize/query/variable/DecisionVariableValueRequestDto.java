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
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
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
