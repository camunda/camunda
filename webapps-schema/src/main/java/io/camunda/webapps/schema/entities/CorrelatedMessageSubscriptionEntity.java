/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities;

import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.time.OffsetDateTime;
import java.util.Objects;

public class CorrelatedMessageSubscriptionEntity
    implements ExporterEntity<CorrelatedMessageSubscriptionEntity>,
        PartitionedEntity<CorrelatedMessageSubscriptionEntity>,
        TenantOwned {

  @SinceVersion880 private String id;

  @SinceVersion880 private String bpmnProcessId;
  @SinceVersion880 private String correlationKey;
  @SinceVersion880 private OffsetDateTime correlationTime;
  @SinceVersion880 private String flowNodeId;
  @SinceVersion880 private Long flowNodeInstanceKey;
  @SinceVersion880 private long messageKey;
  @SinceVersion880 private String messageName;
  @SinceVersion880 private int partitionId;
  @SinceVersion880 private Long position;
  @SinceVersion880 private Long processDefinitionKey;
  @SinceVersion880 private Long processInstanceKey;
  @SinceVersion880 private long subscriptionKey;
  @SinceVersion880 private String subscriptionType;
  @SinceVersion880 private String tenantId;

  @Override
  public String getId() {
    return id;
  }

  @Override
  public CorrelatedMessageSubscriptionEntity setId(final String id) {
    this.id = id;
    return this;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public CorrelatedMessageSubscriptionEntity setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public String getCorrelationKey() {
    return correlationKey;
  }

  public CorrelatedMessageSubscriptionEntity setCorrelationKey(final String correlationKey) {
    this.correlationKey = correlationKey;
    return this;
  }

  public OffsetDateTime getCorrelationTime() {
    return correlationTime;
  }

  public CorrelatedMessageSubscriptionEntity setCorrelationTime(
      final OffsetDateTime correlationTime) {
    this.correlationTime = correlationTime;
    return this;
  }

  public String getFlowNodeId() {
    return flowNodeId;
  }

  public CorrelatedMessageSubscriptionEntity setFlowNodeId(final String flowNodeId) {
    this.flowNodeId = flowNodeId;
    return this;
  }

  public Long getFlowNodeInstanceKey() {
    return flowNodeInstanceKey;
  }

  public CorrelatedMessageSubscriptionEntity setFlowNodeInstanceKey(
      final Long flowNodeInstanceKey) {
    this.flowNodeInstanceKey = flowNodeInstanceKey;
    return this;
  }

  public long getMessageKey() {
    return messageKey;
  }

  public CorrelatedMessageSubscriptionEntity setMessageKey(final long messageKey) {
    this.messageKey = messageKey;
    return this;
  }

  public String getMessageName() {
    return messageName;
  }

  public CorrelatedMessageSubscriptionEntity setMessageName(final String messageName) {
    this.messageName = messageName;
    return this;
  }

  public Long getPosition() {
    return position;
  }

  public CorrelatedMessageSubscriptionEntity setPosition(final Long position) {
    this.position = position;
    return this;
  }

  @Override
  public int getPartitionId() {
    return partitionId;
  }

  @Override
  public CorrelatedMessageSubscriptionEntity setPartitionId(final int partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  public Long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public CorrelatedMessageSubscriptionEntity setProcessDefinitionKey(
      final Long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public CorrelatedMessageSubscriptionEntity setProcessInstanceKey(final Long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public long getSubscriptionKey() {
    return subscriptionKey;
  }

  public CorrelatedMessageSubscriptionEntity setSubscriptionKey(final long subscriptionKey) {
    this.subscriptionKey = subscriptionKey;
    return this;
  }

  public String getSubscriptionType() {
    return subscriptionType;
  }

  public CorrelatedMessageSubscriptionEntity setSubscriptionType(final String subscriptionType) {
    this.subscriptionType = subscriptionType;
    return this;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  public CorrelatedMessageSubscriptionEntity setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        id,
        bpmnProcessId,
        correlationKey,
        correlationTime,
        flowNodeId,
        flowNodeInstanceKey,
        messageKey,
        messageName,
        partitionId,
        position,
        processDefinitionKey,
        processInstanceKey,
        subscriptionKey,
        subscriptionType,
        tenantId);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final CorrelatedMessageSubscriptionEntity that = (CorrelatedMessageSubscriptionEntity) o;
    return messageKey == that.messageKey
        && partitionId == that.partitionId
        && subscriptionKey == that.subscriptionKey
        && Objects.equals(id, that.id)
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(correlationKey, that.correlationKey)
        && Objects.equals(correlationTime, that.correlationTime)
        && Objects.equals(flowNodeId, that.flowNodeId)
        && Objects.equals(flowNodeInstanceKey, that.flowNodeInstanceKey)
        && Objects.equals(messageName, that.messageName)
        && Objects.equals(position, that.position)
        && Objects.equals(processDefinitionKey, that.processDefinitionKey)
        && Objects.equals(processInstanceKey, that.processInstanceKey)
        && Objects.equals(subscriptionType, that.subscriptionType)
        && Objects.equals(tenantId, that.tenantId);
  }

  @Override
  public String toString() {
    return "CorrelatedMessageSubscriptionEntity{"
        + "id='"
        + id
        + '\''
        + ", bpmnProcessId='"
        + bpmnProcessId
        + '\''
        + ", correlationKey='"
        + correlationKey
        + '\''
        + ", correlationTime="
        + correlationTime
        + ", flowNodeId='"
        + flowNodeId
        + '\''
        + ", flowNodeInstanceKey="
        + flowNodeInstanceKey
        + ", messageKey="
        + messageKey
        + ", messageName='"
        + messageName
        + '\''
        + ", partitionId="
        + partitionId
        + ", position="
        + position
        + ", processDefinitionKey="
        + processDefinitionKey
        + ", processInstanceKey="
        + processInstanceKey
        + ", subscriptionKey="
        + subscriptionKey
        + ", subscriptionType='"
        + subscriptionType
        + ", tenantId='"
        + tenantId
        + '\''
        + '}';
  }
}
