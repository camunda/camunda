/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.entities;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.util.ObjectBuilder;
import org.jspecify.annotations.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SequenceFlowEntity(
    String sequenceFlowId,
    String flowNodeId,
    Long processInstanceKey,
    @Nullable Long rootProcessInstanceKey,
    Long processDefinitionKey,
    String processDefinitionId,
    String tenantId)
    implements TenantOwnedEntity {

  public SequenceFlowEntity {
    requireNonNull(sequenceFlowId, "sequenceFlowId");
    requireNonNull(flowNodeId, "flowNodeId");
    requireNonNull(processInstanceKey, "processInstanceKey");
    requireNonNull(processDefinitionKey, "processDefinitionKey");
    requireNonNull(processDefinitionId, "processDefinitionId");
    requireNonNull(tenantId, "tenantId");
  }

  public static class Builder implements ObjectBuilder<SequenceFlowEntity> {

    private String sequenceFlowId;
    private String flowNodeId;
    private Long processInstanceKey;
    private Long rootProcessInstanceKey;
    private Long processDefinitionKey;
    private String processDefinitionId;
    private String tenantId;

    public Builder sequenceFlowId(final String sequenceFlowId) {
      this.sequenceFlowId = sequenceFlowId;
      return this;
    }

    public Builder flowNodeId(final String flowNodeId) {
      this.flowNodeId = flowNodeId;
      return this;
    }

    public Builder processInstanceKey(final Long processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
      return this;
    }

    public Builder rootProcessInstanceKey(final Long rootProcessInstanceKey) {
      this.rootProcessInstanceKey = rootProcessInstanceKey;
      return this;
    }

    public Builder processDefinitionKey(final Long processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return this;
    }

    public Builder processDefinitionId(final String processDefinitionId) {
      this.processDefinitionId = processDefinitionId;
      return this;
    }

    public Builder tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    @Override
    public SequenceFlowEntity build() {
      return new SequenceFlowEntity(
          sequenceFlowId,
          flowNodeId,
          processInstanceKey,
          rootProcessInstanceKey,
          processDefinitionKey,
          processDefinitionId,
          tenantId);
    }
  }
}
