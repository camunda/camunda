/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public record SequenceFlowDbModel(
    String flowNodeId,
    Long processInstanceKey,
    Long rootProcessInstanceKey,
    Long processDefinitionKey,
    String processDefinitionId,
    String tenantId,
    int partitionId)
    implements DbModel<SequenceFlowDbModel> {

  private static final String ID_PATTERN = "%s_%s";

  // Used by the search resultMap, which never returns PARTITION_ID (write-only, insert-only
  // column) -- mirrors the previous no-arg-constructor-plus-setters behavior where an unset int
  // field defaulted to 0.
  public SequenceFlowDbModel(
      final String flowNodeId,
      final Long processInstanceKey,
      final Long rootProcessInstanceKey,
      final Long processDefinitionKey,
      final String processDefinitionId,
      final String tenantId) {
    this(
        flowNodeId,
        processInstanceKey,
        rootProcessInstanceKey,
        processDefinitionKey,
        processDefinitionId,
        tenantId,
        0);
  }

  public String sequenceFlowId() {
    return ID_PATTERN.formatted(processInstanceKey, flowNodeId);
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
                .tenantId(tenantId)
                .partitionId(partitionId))
        .build();
  }

  // partitionId is broker-partition metadata, not part of this entity's identity -- two
  // otherwise-identical flow records must remain equal regardless of which partition wrote them.
  // toString() is overridden to match, so it doesn't print a field the type deliberately excludes
  // from identity.
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

    @Override
    public SequenceFlowDbModel build() {
      return new SequenceFlowDbModel(
          flowNodeId,
          processInstanceKey,
          rootProcessInstanceKey,
          processDefinitionKey,
          processDefinitionId,
          tenantId,
          partitionId);
    }
  }
}
