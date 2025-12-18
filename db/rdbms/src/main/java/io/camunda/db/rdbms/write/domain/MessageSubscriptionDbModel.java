/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import io.camunda.search.entities.MessageSubscriptionEntity.MessageSubscriptionState;
import io.camunda.util.ObjectBuilder;
import java.time.OffsetDateTime;
import java.util.function.Function;

public class MessageSubscriptionDbModel implements Copyable<MessageSubscriptionDbModel> {
  private Long messageSubscriptionKey;
  private String processDefinitionId;
  private Long processDefinitionKey;
  private Long processInstanceKey;
  private Long rootProcessInstanceKey;
  private String flowNodeId;
  private Long flowNodeInstanceKey;
  private MessageSubscriptionState messageSubscriptionState;
  private OffsetDateTime dateTime;
  private String messageName;
  private String correlationKey;
  private String tenantId;
  private int partitionId;
  private OffsetDateTime historyCleanupDate;

  public MessageSubscriptionDbModel(final Long messageSubscriptionKey) {
    this.messageSubscriptionKey = messageSubscriptionKey;
  }

  public MessageSubscriptionDbModel(
      final Long messageSubscriptionKey,
      final String processDefinitionId,
      final Long processDefinitionKey,
      final Long processInstanceKey,
      final Long rootProcessInstanceKey,
      final String flowNodeId,
      final Long flowNodeInstanceKey,
      final MessageSubscriptionState messageSubscriptionState,
      final OffsetDateTime dateTime,
      final String messageName,
      final String correlationKey,
      final String tenantId,
      final int partitionId,
      final OffsetDateTime historyCleanupDate) {
    this.messageSubscriptionKey = messageSubscriptionKey;
    this.processDefinitionId = processDefinitionId;
    this.processDefinitionKey = processDefinitionKey;
    this.rootProcessInstanceKey = rootProcessInstanceKey;
    this.processInstanceKey = processInstanceKey;
    this.flowNodeId = flowNodeId;
    this.flowNodeInstanceKey = flowNodeInstanceKey;
    this.messageSubscriptionState = messageSubscriptionState;
    this.dateTime = dateTime;
    this.messageName = messageName;
    this.correlationKey = correlationKey;
    this.tenantId = tenantId;
    this.partitionId = partitionId;
    this.historyCleanupDate = historyCleanupDate;
  }

  public Long messageSubscriptionKey() {
    return messageSubscriptionKey;
  }

  public void messageSubscriptionKey(final Long messageSubscriptionKey) {
    this.messageSubscriptionKey = messageSubscriptionKey;
  }

  public String processDefinitionId() {
    return processDefinitionId;
  }

  public void processDefinitionId(final String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
  }

  public Long processDefinitionKey() {
    return processDefinitionKey;
  }

  public void processDefinitionKey(final Long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public Long processInstanceKey() {
    return processInstanceKey;
  }

  public void processInstanceKey(final Long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
  }

  public Long rootProcessInstanceKey() {
    return rootProcessInstanceKey;
  }

  public void rootProcessInstanceKey(final Long rootProcessInstanceKey) {
    this.rootProcessInstanceKey = rootProcessInstanceKey;
  }

  public String flowNodeId() {
    return flowNodeId;
  }

  public void flowNodeId(final String flowNodeId) {
    this.flowNodeId = flowNodeId;
  }

  public Long flowNodeInstanceKey() {
    return flowNodeInstanceKey;
  }

  public void flowNodeInstanceKey(final Long flowNodeInstanceKey) {
    this.flowNodeInstanceKey = flowNodeInstanceKey;
  }

  public MessageSubscriptionState messageSubscriptionState() {
    return messageSubscriptionState;
  }

  public void messageSubscriptionState(final MessageSubscriptionState messageSubscriptionState) {
    this.messageSubscriptionState = messageSubscriptionState;
  }

  public OffsetDateTime dateTime() {
    return dateTime;
  }

  public void dateTime(final OffsetDateTime dateTime) {
    this.dateTime = dateTime;
  }

  public String messageName() {
    return messageName;
  }

  public void messageName(final String messageName) {
    this.messageName = messageName;
  }

  public String correlationKey() {
    return correlationKey;
  }

  public void correlationKey(final String correlationKey) {
    this.correlationKey = correlationKey;
  }

  public String tenantId() {
    return tenantId;
  }

  public void tenantId(final String tenantId) {
    this.tenantId = tenantId;
  }

  public int partitionId() {
    return partitionId;
  }

  public MessageSubscriptionDbModel partitionId(final int partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  public OffsetDateTime historyCleanupDate() {
    return historyCleanupDate;
  }

  public MessageSubscriptionDbModel historyCleanupDate(final OffsetDateTime historyCleanupDate) {
    this.historyCleanupDate = historyCleanupDate;
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
        .rootProcessInstanceKey(rootProcessInstanceKey)
        .flowNodeId(flowNodeId)
        .flowNodeInstanceKey(flowNodeInstanceKey)
        .messageSubscriptionState(messageSubscriptionState)
        .dateTime(dateTime)
        .messageName(messageName)
        .correlationKey(correlationKey)
        .tenantId(tenantId)
        .partitionId(partitionId)
        .historyCleanupDate(historyCleanupDate);
  }

  public static class Builder implements ObjectBuilder<MessageSubscriptionDbModel> {
    private Long messageSubscriptionKey;
    private String processDefinitionId;
    private Long processDefinitionKey;
    private Long processInstanceKey;
    private Long rootProcessInstanceKey;
    private String flowNodeId;
    private Long flowNodeInstanceKey;
    private MessageSubscriptionState messageSubscriptionState;
    private OffsetDateTime dateTime;
    private String messageName;
    private String correlationKey;
    private String tenantId;
    private int partitionId;
    private OffsetDateTime historyCleanupDate;

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

    public Builder rootProcessInstanceKey(final Long rootProcessInstanceKey) {
      this.rootProcessInstanceKey = rootProcessInstanceKey;
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

    public Builder messageSubscriptionState(
        final MessageSubscriptionState messageSubscriptionState) {
      this.messageSubscriptionState = messageSubscriptionState;
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

    public Builder historyCleanupDate(final OffsetDateTime historyCleanupDate) {
      this.historyCleanupDate = historyCleanupDate;
      return this;
    }

    @Override
    public MessageSubscriptionDbModel build() {
      return new MessageSubscriptionDbModel(
          messageSubscriptionKey,
          processDefinitionId,
          processDefinitionKey,
          processInstanceKey,
          rootProcessInstanceKey,
          flowNodeId,
          flowNodeInstanceKey,
          messageSubscriptionState,
          dateTime,
          messageName,
          correlationKey,
          tenantId,
          partitionId,
          historyCleanupDate);
    }
  }
}
