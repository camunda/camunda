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

public class CorrelatedMessageDbModel implements Copyable<CorrelatedMessageDbModel> {
  private Long messageKey;
  private Long subscriptionKey;
  private String messageName;
  private String correlationKey;
  private Long processInstanceKey;
  private Long flowNodeInstanceKey;
  private String startEventId;
  private String elementId;
  private Boolean isInterrupting;
  private Long processDefinitionKey;
  private String bpmnProcessId;
  private String variables;
  private String tenantId;
  private OffsetDateTime dateTime;
  private int partitionId;
  private OffsetDateTime historyCleanupDate;

  public CorrelatedMessageDbModel(final Long messageKey, final Long subscriptionKey) {
    this.messageKey = messageKey;
    this.subscriptionKey = subscriptionKey;
  }

  public CorrelatedMessageDbModel(
      final Long messageKey,
      final Long subscriptionKey,
      final String messageName,
      final String correlationKey,
      final Long processInstanceKey,
      final Long flowNodeInstanceKey,
      final String startEventId,
      final String elementId,
      final Boolean isInterrupting,
      final Long processDefinitionKey,
      final String bpmnProcessId,
      final String variables,
      final String tenantId,
      final OffsetDateTime dateTime,
      final int partitionId,
      final OffsetDateTime historyCleanupDate) {
    this.messageKey = messageKey;
    this.subscriptionKey = subscriptionKey;
    this.messageName = messageName;
    this.correlationKey = correlationKey;
    this.processInstanceKey = processInstanceKey;
    this.flowNodeInstanceKey = flowNodeInstanceKey;
    this.startEventId = startEventId;
    this.elementId = elementId;
    this.isInterrupting = isInterrupting;
    this.processDefinitionKey = processDefinitionKey;
    this.bpmnProcessId = bpmnProcessId;
    this.variables = variables;
    this.tenantId = tenantId;
    this.dateTime = dateTime;
    this.partitionId = partitionId;
    this.historyCleanupDate = historyCleanupDate;
  }

  public Long messageKey() {
    return messageKey;
  }

  public void messageKey(final Long messageKey) {
    this.messageKey = messageKey;
  }

  public Long subscriptionKey() {
    return subscriptionKey;
  }

  public void subscriptionKey(final Long subscriptionKey) {
    this.subscriptionKey = subscriptionKey;
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

  public String startEventId() {
    return startEventId;
  }

  public void startEventId(final String startEventId) {
    this.startEventId = startEventId;
  }

  public String elementId() {
    return elementId;
  }

  public void elementId(final String elementId) {
    this.elementId = elementId;
  }

  public Boolean isInterrupting() {
    return isInterrupting;
  }

  public void isInterrupting(final Boolean isInterrupting) {
    this.isInterrupting = isInterrupting;
  }

  public Long processDefinitionKey() {
    return processDefinitionKey;
  }

  public void processDefinitionKey(final Long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public String bpmnProcessId() {
    return bpmnProcessId;
  }

  public void bpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
  }

  public String variables() {
    return variables;
  }

  public void variables(final String variables) {
    this.variables = variables;
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
  public CorrelatedMessageDbModel copy(
      final Function<
              ObjectBuilder<CorrelatedMessageDbModel>, ObjectBuilder<CorrelatedMessageDbModel>>
          copyFunction) {
    return copyFunction.apply(toBuilder()).build();
  }

  public Builder toBuilder() {
    return new Builder()
        .messageKey(messageKey)
        .subscriptionKey(subscriptionKey)
        .messageName(messageName)
        .correlationKey(correlationKey)
        .processInstanceKey(processInstanceKey)
        .flowNodeInstanceKey(flowNodeInstanceKey)
        .startEventId(startEventId)
        .elementId(elementId)
        .isInterrupting(isInterrupting)
        .processDefinitionKey(processDefinitionKey)
        .bpmnProcessId(bpmnProcessId)
        .variables(variables)
        .tenantId(tenantId)
        .dateTime(dateTime)
        .partitionId(partitionId)
        .historyCleanupDate(historyCleanupDate);
  }

  public static CorrelatedMessageDbModel of(
      final Function<Builder, ObjectBuilder<CorrelatedMessageDbModel>> builderFunction) {
    return builderFunction.apply(new Builder()).build();
  }

  public static class Builder implements ObjectBuilder<CorrelatedMessageDbModel> {
    private Long messageKey;
    private Long subscriptionKey;
    private String messageName;
    private String correlationKey;
    private Long processInstanceKey;
    private Long flowNodeInstanceKey;
    private String startEventId;
    private String bpmnProcessId;
    private String variables;
    private String tenantId;
    private OffsetDateTime dateTime;
    private int partitionId;
    private OffsetDateTime historyCleanupDate;

    public Builder messageKey(final Long messageKey) {
      this.messageKey = messageKey;
      return this;
    }

    public Builder subscriptionKey(final Long subscriptionKey) {
      this.subscriptionKey = subscriptionKey;
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

    public Builder startEventId(final String startEventId) {
      this.startEventId = startEventId;
      return this;
    }

    public Builder bpmnProcessId(final String bpmnProcessId) {
      this.bpmnProcessId = bpmnProcessId;
      return this;
    }

    public Builder variables(final String variables) {
      this.variables = variables;
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
    public CorrelatedMessageDbModel build() {
      return new CorrelatedMessageDbModel(
          messageKey,
          subscriptionKey,
          messageName,
          correlationKey,
          processInstanceKey,
          flowNodeInstanceKey,
          startEventId,
          elementId,
          isInterrupting,
          processDefinitionKey,
          bpmnProcessId,
          variables,
          tenantId,
          dateTime,
          partitionId,
          historyCleanupDate);
    }
  }
}
