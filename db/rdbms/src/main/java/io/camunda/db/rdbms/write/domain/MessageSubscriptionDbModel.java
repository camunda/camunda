/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import io.camunda.search.entities.MessageSubscriptionEntity.MessageSubscriptionType;
import io.camunda.util.ObjectBuilder;
import java.time.OffsetDateTime;
import java.util.function.Function;

public class MessageSubscriptionDbModel implements Copyable<MessageSubscriptionDbModel> {
  private Long messageSubscriptionKey;
  private String processDefinitionId;
  private Long processDefinitionKey;
  private Long processInstanceKey;
  private String flowNodeId;
  private Long flowNodeInstanceKey;
  private MessageSubscriptionType messageSubscriptionType;
  private OffsetDateTime dateTime;
  private String messageName;
  private String correlationKey;
  private String tenantId;
  private int partitionId;

  public MessageSubscriptionDbModel(final Long messageSubscriptionKey) {
    this.messageSubscriptionKey = messageSubscriptionKey;
  }

  public MessageSubscriptionDbModel(
      final Long messageSubscriptionKey,
      final String processDefinitionId,
      final Long processDefinitionKey,
      final Long processInstanceKey,
      final String flowNodeId,
      final Long flowNodeInstanceKey,
      final MessageSubscriptionType messageSubscriptionType,
      final OffsetDateTime dateTime,
      final String messageName,
      final String correlationKey,
      final String tenantId,
      final int partitionId) {
    this.messageSubscriptionKey = messageSubscriptionKey;
    this.processDefinitionId = processDefinitionId;
    this.processDefinitionKey = processDefinitionKey;
    this.processInstanceKey = processInstanceKey;
    this.flowNodeId = flowNodeId;
    this.flowNodeInstanceKey = flowNodeInstanceKey;
    this.messageSubscriptionType = messageSubscriptionType;
    this.dateTime = dateTime;
    this.messageName = messageName;
    this.correlationKey = correlationKey;
    this.tenantId = tenantId;
    this.partitionId = partitionId;
  }

  public Long messageSubscriptionKey() {
    return messageSubscriptionKey;
  }

  public void setMessageSubscriptionKey(final Long messageSubscriptionKey) {
    this.messageSubscriptionKey = messageSubscriptionKey;
  }

  public String processDefinitionId() {
    return processDefinitionId;
  }

  public void setProcessDefinitionId(final String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
  }

  public Long processDefinitionKey() {
    return processDefinitionKey;
  }

  public void setProcessDefinitionKey(final Long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public Long processInstanceKey() {
    return processInstanceKey;
  }

  public void setProcessInstanceKey(final Long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
  }

  public String flowNodeId() {
    return flowNodeId;
  }

  public void setFlowNodeId(final String flowNodeId) {
    this.flowNodeId = flowNodeId;
  }

  public Long flowNodeInstanceKey() {
    return flowNodeInstanceKey;
  }

  public void setFlowNodeInstanceKey(final Long flowNodeInstanceKey) {
    this.flowNodeInstanceKey = flowNodeInstanceKey;
  }

  public MessageSubscriptionType messageSubscriptionType() {
    return messageSubscriptionType;
  }

  public void setMessageSubscriptionType(final MessageSubscriptionType messageSubscriptionType) {
    this.messageSubscriptionType = messageSubscriptionType;
  }

  public OffsetDateTime dateTime() {
    return dateTime;
  }

  public void setDateTime(final OffsetDateTime dateTime) {
    this.dateTime = dateTime;
  }

  public String messageName() {
    return messageName;
  }

  public void setMessageName(final String messageName) {
    this.messageName = messageName;
  }

  public String correlationKey() {
    return correlationKey;
  }

  public void setCorrelationKey(final String correlationKey) {
    this.correlationKey = correlationKey;
  }

  public String tenantId() {
    return tenantId;
  }

  public void setTenantId(final String tenantId) {
    this.tenantId = tenantId;
  }

  public int partitionId() {
    return partitionId;
  }

  public MessageSubscriptionDbModel setPartitionId(final int partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  @Override
  public MessageSubscriptionDbModel copy(
      final Function<
              ObjectBuilder<MessageSubscriptionDbModel>, ObjectBuilder<MessageSubscriptionDbModel>>
          copyFunction) {
    return copyFunction.apply(toBuilder()).build();
  }

  public ObjectBuilder<MessageSubscriptionDbModel> toBuilder() {
    return new Builder()
        .messageSubscriptionKey(messageSubscriptionKey)
        .processDefinitionId(processDefinitionId)
        .processDefinitionKey(processDefinitionKey)
        .processInstanceKey(processInstanceKey)
        .flowNodeId(flowNodeId)
        .flowNodeInstanceKey(flowNodeInstanceKey)
        .messageSubscriptionType(messageSubscriptionType)
        .dateTime(dateTime)
        .messageName(messageName)
        .correlationKey(correlationKey)
        .tenantId(tenantId)
        .partitionId(partitionId);
  }

  public static class Builder implements ObjectBuilder<MessageSubscriptionDbModel> {
    private Long messageSubscriptionKey;
    private String processDefinitionId;
    private Long processDefinitionKey;
    private Long processInstanceKey;
    private String flowNodeId;
    private Long flowNodeInstanceKey;
    private MessageSubscriptionType messageSubscriptionType;
    private OffsetDateTime dateTime;
    private String messageName;
    private String correlationKey;
    private String tenantId;
    private int partitionId;

    public Builder messageSubscriptionKey(final Long messageSubscriptionKey) {
      this.messageSubscriptionKey = messageSubscriptionKey;
      return this;
    }

    public Builder processDefinitionId(final String processDefinitionId) {
      this.processDefinitionId = processDefinitionId;
      return this;
    }

    public Builder processDefinitionKey(final Long processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return this;
    }

    public Builder processInstanceKey(final Long processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
      return this;
    }

    public Builder flowNodeId(final String flowNodeId) {
      this.flowNodeId = flowNodeId;
      return this;
    }

    public Builder flowNodeInstanceKey(final Long flowNodeInstanceKey) {
      this.flowNodeInstanceKey = flowNodeInstanceKey;
      return this;
    }

    public Builder messageSubscriptionType(final MessageSubscriptionType messageSubscriptionType) {
      this.messageSubscriptionType = messageSubscriptionType;
      return this;
    }

    public Builder dateTime(final OffsetDateTime dateTime) {
      this.dateTime = dateTime;
      return this;
    }

    public Builder messageName(final String messageName) {
      this.messageName = messageName;
      return this;
    }

    public Builder correlationKey(final String correlationKey) {
      this.correlationKey = correlationKey;
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
    public MessageSubscriptionDbModel build() {
      return new MessageSubscriptionDbModel(
          messageSubscriptionKey,
          processDefinitionId,
          processDefinitionKey,
          processInstanceKey,
          flowNodeId,
          flowNodeInstanceKey,
          messageSubscriptionType,
          dateTime,
          messageName,
          correlationKey,
          tenantId,
          partitionId);
    }
  }
}
