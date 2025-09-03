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
import java.util.function.Function;

public class MessageCorrelationDbModel implements Copyable<MessageCorrelationDbModel> {
  private Long subscriptionKey;
  private Long messageKey;
  private String messageName;
  private String correlationKey;
  private Long processInstanceKey;
  private Long flowNodeInstanceKey;
  private String flowNodeId;
  private String bpmnProcessId;
  private Long processDefinitionKey;
  private String tenantId;
  private OffsetDateTime dateTime;
  private int partitionId;
  private OffsetDateTime historyCleanupDate;

  public MessageCorrelationDbModel(final Long subscriptionKey, final Long messageKey) {
    this.subscriptionKey = subscriptionKey;
    this.messageKey = messageKey;
  }

  public MessageCorrelationDbModel(
      final Long subscriptionKey,
      final Long messageKey,
      final String messageName,
      final String correlationKey,
      final Long processInstanceKey,
      final Long flowNodeInstanceKey,
      final String flowNodeId,
      final String bpmnProcessId,
      final Long processDefinitionKey,
      final String tenantId,
      final OffsetDateTime dateTime,
      final int partitionId,
      final OffsetDateTime historyCleanupDate) {
    this.subscriptionKey = subscriptionKey;
    this.messageKey = messageKey;
    this.messageName = messageName;
    this.correlationKey = correlationKey;
    this.processInstanceKey = processInstanceKey;
    this.flowNodeInstanceKey = flowNodeInstanceKey;
    this.flowNodeId = flowNodeId;
    this.bpmnProcessId = bpmnProcessId;
    this.processDefinitionKey = processDefinitionKey;
    this.tenantId = tenantId;
    this.dateTime = dateTime;
    this.partitionId = partitionId;
    this.historyCleanupDate = historyCleanupDate;
  }

  public Long subscriptionKey() {
    return subscriptionKey;
  }

  public void subscriptionKey(final Long subscriptionKey) {
    this.subscriptionKey = subscriptionKey;
  }

  public Long messageKey() {
    return messageKey;
  }

  public void messageKey(final Long messageKey) {
    this.messageKey = messageKey;
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

  public Long processInstanceKey() {
    return processInstanceKey;
  }

  public void processInstanceKey(final Long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
  }

  public Long flowNodeInstanceKey() {
    return flowNodeInstanceKey;
  }

  public void flowNodeInstanceKey(final Long flowNodeInstanceKey) {
    this.flowNodeInstanceKey = flowNodeInstanceKey;
  }

  public String flowNodeId() {
    return flowNodeId;
  }

  public void flowNodeId(final String flowNodeId) {
    this.flowNodeId = flowNodeId;
  }

  public String bpmnProcessId() {
    return bpmnProcessId;
  }

  public void bpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
  }

  public Long processDefinitionKey() {
    return processDefinitionKey;
  }

  public void processDefinitionKey(final Long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public String tenantId() {
    return tenantId;
  }

  public void tenantId(final String tenantId) {
    this.tenantId = tenantId;
  }

  public OffsetDateTime dateTime() {
    return dateTime;
  }

  public void dateTime(final OffsetDateTime dateTime) {
    this.dateTime = dateTime;
  }

  public int partitionId() {
    return partitionId;
  }

  public MessageCorrelationDbModel partitionId(final int partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  public OffsetDateTime historyCleanupDate() {
    return historyCleanupDate;
  }

  public MessageCorrelationDbModel historyCleanupDate(final OffsetDateTime historyCleanupDate) {
    this.historyCleanupDate = historyCleanupDate;
    return this;
  }

  @Override
  public MessageCorrelationDbModel copy(
      final Function<
              ObjectBuilder<MessageCorrelationDbModel>, ObjectBuilder<MessageCorrelationDbModel>>
          copyFunction) {
    return copyFunction.apply(toBuilder()).build();
  }

  public ObjectBuilder<MessageCorrelationDbModel> toBuilder() {
    return new Builder()
        .subscriptionKey(subscriptionKey)
        .messageKey(messageKey)
        .messageName(messageName)
        .correlationKey(correlationKey)
        .processInstanceKey(processInstanceKey)
        .flowNodeInstanceKey(flowNodeInstanceKey)
        .flowNodeId(flowNodeId)
        .bpmnProcessId(bpmnProcessId)
        .processDefinitionKey(processDefinitionKey)
        .tenantId(tenantId)
        .dateTime(dateTime)
        .partitionId(partitionId)
        .historyCleanupDate(historyCleanupDate);
  }

  public static class Builder implements ObjectBuilder<MessageCorrelationDbModel> {
    private Long subscriptionKey;
    private Long messageKey;
    private String messageName;
    private String correlationKey;
    private Long processInstanceKey;
    private Long flowNodeInstanceKey;
    private String flowNodeId;
    private String bpmnProcessId;
    private Long processDefinitionKey;
    private String tenantId;
    private OffsetDateTime dateTime;
    private int partitionId;
    private OffsetDateTime historyCleanupDate;

    public Builder subscriptionKey(final Long subscriptionKey) {
      this.subscriptionKey = subscriptionKey;
      return this;
    }

    public Builder messageKey(final Long messageKey) {
      this.messageKey = messageKey;
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

    public Builder processInstanceKey(final Long processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
      return this;
    }

    public Builder flowNodeInstanceKey(final Long flowNodeInstanceKey) {
      this.flowNodeInstanceKey = flowNodeInstanceKey;
      return this;
    }

    public Builder flowNodeId(final String flowNodeId) {
      this.flowNodeId = flowNodeId;
      return this;
    }

    public Builder bpmnProcessId(final String bpmnProcessId) {
      this.bpmnProcessId = bpmnProcessId;
      return this;
    }

    public Builder processDefinitionKey(final Long processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return this;
    }

    public Builder tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    public Builder dateTime(final OffsetDateTime dateTime) {
      this.dateTime = dateTime;
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
    public MessageCorrelationDbModel build() {
      return new MessageCorrelationDbModel(
          subscriptionKey,
          messageKey,
          messageName,
          correlationKey,
          processInstanceKey,
          flowNodeInstanceKey,
          flowNodeId,
          bpmnProcessId,
          processDefinitionKey,
          tenantId,
          dateTime,
          partitionId,
          historyCleanupDate);
    }
  }
}