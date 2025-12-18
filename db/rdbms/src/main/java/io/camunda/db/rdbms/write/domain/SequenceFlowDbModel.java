/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import io.camunda.util.ObjectBuilder;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.function.Function;

public final class SequenceFlowDbModel implements DbModel<SequenceFlowDbModel> {

  private static final String ID_PATTERN = "%s_%s";
  private String flowNodeId;
  private Long processInstanceKey;
  private Long rootProcessInstanceKey;
  private Long processDefinitionKey;
  private String processDefinitionId;
  private String tenantId;
  private int partitionId;
  private OffsetDateTime historyCleanupDate;

  public String sequenceFlowId() {
    return ID_PATTERN.formatted(processInstanceKey, flowNodeId);
  }

  public void flowNodeId(final String flowNodeId) {
    this.flowNodeId = flowNodeId;
  }

  public void processInstanceKey(final Long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
  }

  public void rootProcessInstanceKey(final Long rootProcessInstanceKey) {
    this.rootProcessInstanceKey = rootProcessInstanceKey;
  }

  public void processDefinitionKey(final Long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public void processDefinitionId(final String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
  }

  public void tenantId(final String tenantId) {
    this.tenantId = tenantId;
  }

  public int partitionId() {
    return partitionId;
  }

  public void partitionId(final int partitionId) {
    this.partitionId = partitionId;
  }

  public OffsetDateTime historyCleanupDate() {
    return historyCleanupDate;
  }

  public void historyCleanupDate(final OffsetDateTime historyCleanupDate) {
    this.historyCleanupDate = historyCleanupDate;
  }

  @Override
  public SequenceFlowDbModel copy(
      final Function<ObjectBuilder<SequenceFlowDbModel>, ObjectBuilder<SequenceFlowDbModel>>
          copyFunction) {
    return copyFunction
        .apply(
            new Builder()
                .flowNodeId(flowNodeId)
                .processInstanceKey(processInstanceKey)
                .rootProcessInstanceKey(rootProcessInstanceKey)
                .processDefinitionKey(processDefinitionKey)
                .processDefinitionId(processDefinitionId)
                .tenantId(tenantId))
        .build();
  }

  public String flowNodeId() {
    return flowNodeId;
  }

  public Long processInstanceKey() {
    return processInstanceKey;
  }

  public Long rootProcessInstanceKey() {
    return rootProcessInstanceKey;
  }

  public Long processDefinitionKey() {
    return processDefinitionKey;
  }

  public String processDefinitionId() {
    return processDefinitionId;
  }

  public String tenantId() {
    return tenantId;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        flowNodeId,
        processInstanceKey,
        rootProcessInstanceKey,
        processDefinitionKey,
        processDefinitionId,
        tenantId);
  }

  @Override
  public boolean equals(final Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj == null || obj.getClass() != getClass()) {
      return false;
    }
    final var that = (SequenceFlowDbModel) obj;
    return Objects.equals(flowNodeId, that.flowNodeId)
        && Objects.equals(processInstanceKey, that.processInstanceKey)
        && Objects.equals(rootProcessInstanceKey, that.rootProcessInstanceKey)
        && Objects.equals(processDefinitionKey, that.processDefinitionKey)
        && Objects.equals(processDefinitionId, that.processDefinitionId)
        && Objects.equals(tenantId, that.tenantId);
  }

  @Override
  public String toString() {
    return "SequenceFlowDbModel[flowNodeId=%s, processInstanceKey=%d, rootProcessInstanceKey=%d, processDefinitionKey=%d, processDefinitionId=%s, tenantId=%s]"
        .formatted(
            flowNodeId,
            processInstanceKey,
            rootProcessInstanceKey,
            processDefinitionKey,
            processDefinitionId,
            tenantId);
  }

  public static class Builder implements ObjectBuilder<SequenceFlowDbModel> {

    private String flowNodeId;
    private Long processInstanceKey;
    private Long rootProcessInstanceKey;
    private Long processDefinitionKey;
    private String processDefinitionId;
    private String tenantId;
    private int partitionId;
    private OffsetDateTime historyCleanupDate;

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

    public Builder partitionId(final int partitionId) {
      this.partitionId = partitionId;
      return this;
    }

    public Builder historyCleanupDate(final OffsetDateTime historyCleanupDate) {
      this.historyCleanupDate = historyCleanupDate;
      return this;
    }

    @Override
    public SequenceFlowDbModel build() {
      final var dbModel = new SequenceFlowDbModel();
      dbModel.flowNodeId(flowNodeId);
      dbModel.processInstanceKey(processInstanceKey);
      dbModel.rootProcessInstanceKey(rootProcessInstanceKey);
      dbModel.processDefinitionKey(processDefinitionKey);
      dbModel.processDefinitionId(processDefinitionId);
      dbModel.tenantId(tenantId);
      dbModel.partitionId(partitionId);
      dbModel.historyCleanupDate(historyCleanupDate);
      return dbModel;
    }
  }
}
