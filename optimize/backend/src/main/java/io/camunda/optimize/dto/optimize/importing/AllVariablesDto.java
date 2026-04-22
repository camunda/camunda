/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.importing;

import io.camunda.optimize.dto.optimize.OptimizeDto;

/** One document per Zeebe variable record in the {@code optimize-all-variables} index. */
public class AllVariablesDto implements OptimizeDto {

  private String variableKey;
  private String processInstanceKey;
  private String processDefinitionKey;
  private String scopeKey;
  private String tenantId;
  private String name;
  private String value;
  private Long timestamp;
  private Integer partitionId;
  private Long sequence;

  public String getVariableKey() {
    return variableKey;
  }

  public void setVariableKey(final String variableKey) {
    this.variableKey = variableKey;
  }

  public String getProcessInstanceKey() {
    return processInstanceKey;
  }

  public void setProcessInstanceKey(final String processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
  }

  public String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public void setProcessDefinitionKey(final String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public String getScopeKey() {
    return scopeKey;
  }

  public void setScopeKey(final String scopeKey) {
    this.scopeKey = scopeKey;
  }

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(final String tenantId) {
    this.tenantId = tenantId;
  }

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public String getValue() {
    return value;
  }

  public void setValue(final String value) {
    this.value = value;
  }

  public Long getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(final Long timestamp) {
    this.timestamp = timestamp;
  }

  public Integer getPartitionId() {
    return partitionId;
  }

  public void setPartitionId(final Integer partitionId) {
    this.partitionId = partitionId;
  }

  public Long getSequence() {
    return sequence;
  }

  public void setSequence(final Long sequence) {
    this.sequence = sequence;
  }
}
