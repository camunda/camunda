/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.gateway.protocol.model.simple.AuditLogFilter;
import org.springframework.lang.Nullable;

public class McpAuditLogFilter extends AuditLogFilter {

  @JsonIgnore
  @Override
  public @Nullable String getAuditLogKey() {
    return super.getAuditLogKey();
  }

  @JsonIgnore
  @Override
  public @Nullable String getProcessDefinitionKey() {
    return super.getProcessDefinitionKey();
  }

  @JsonIgnore
  @Override
  public @Nullable String getProcessInstanceKey() {
    return super.getProcessInstanceKey();
  }

  @JsonIgnore
  @Override
  public @Nullable String getElementInstanceKey() {
    return super.getElementInstanceKey();
  }

  @JsonIgnore
  @Override
  public @Nullable io.camunda.gateway.protocol.model.AuditLogOperationTypeEnum getOperationType() {
    return super.getOperationType();
  }

  @JsonIgnore
  @Override
  public @Nullable io.camunda.gateway.protocol.model.AuditLogResultEnum getResult() {
    return super.getResult();
  }

  @JsonIgnore
  @Override
  public @Nullable String getActorId() {
    return super.getActorId();
  }

  @JsonIgnore
  @Override
  public @Nullable io.camunda.gateway.protocol.model.AuditLogActorTypeEnum getActorType() {
    return super.getActorType();
  }

  @JsonIgnore
  @Override
  public @Nullable String getEntityKey() {
    return super.getEntityKey();
  }

  @JsonIgnore
  @Override
  public @Nullable io.camunda.gateway.protocol.model.AuditLogEntityTypeEnum getEntityType() {
    return super.getEntityType();
  }

  @JsonIgnore
  @Override
  public @Nullable String getTenantId() {
    return super.getTenantId();
  }

  @JsonIgnore
  @Override
  public @Nullable io.camunda.gateway.protocol.model.AuditLogCategoryEnum getCategory() {
    return super.getCategory();
  }

  @JsonIgnore
  @Override
  public @Nullable String getDeploymentKey() {
    return super.getDeploymentKey();
  }

  @JsonIgnore
  @Override
  public @Nullable String getFormKey() {
    return super.getFormKey();
  }

  @JsonIgnore
  @Override
  public @Nullable String getResourceKey() {
    return super.getResourceKey();
  }

  @JsonIgnore
  @Override
  public @Nullable io.camunda.gateway.protocol.model.BatchOperationTypeEnum
      getBatchOperationType() {
    return super.getBatchOperationType();
  }

  @JsonIgnore
  @Override
  public @Nullable String getProcessDefinitionId() {
    return super.getProcessDefinitionId();
  }

  @JsonIgnore
  @Override
  public @Nullable String getJobKey() {
    return super.getJobKey();
  }

  @JsonIgnore
  @Override
  public @Nullable String getUserTaskKey() {
    return super.getUserTaskKey();
  }

  @JsonIgnore
  @Override
  public @Nullable String getDecisionRequirementsId() {
    return super.getDecisionRequirementsId();
  }

  @JsonIgnore
  @Override
  public @Nullable String getDecisionRequirementsKey() {
    return super.getDecisionRequirementsKey();
  }

  @JsonIgnore
  @Override
  public @Nullable String getDecisionDefinitionId() {
    return super.getDecisionDefinitionId();
  }

  @JsonIgnore
  @Override
  public @Nullable String getDecisionDefinitionKey() {
    return super.getDecisionDefinitionKey();
  }

  @JsonIgnore
  @Override
  public @Nullable String getDecisionEvaluationKey() {
    return super.getDecisionEvaluationKey();
  }
}
