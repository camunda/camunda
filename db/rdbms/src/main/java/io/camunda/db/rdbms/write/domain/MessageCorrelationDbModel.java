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

public record MessageCorrelationDbModel(
    String correlationKey,
    OffsetDateTime correlationTime,
    String flowNodeId,
    Long flowNodeInstanceKey,
    OffsetDateTime historyCleanupDate,
    Long messageKey,
    String messageName,
    int partitionId,
    String processDefinitionId,
    Long processDefinitionKey,
    Long processInstanceKey,
    Long subscriptionKey,
    String tenantId)
    implements DbModel<MessageCorrelationDbModel> {

  @Override
  public MessageCorrelationDbModel copy(
      final Function<ObjectBuilder<MessageCorrelationDbModel>, ObjectBuilder<MessageCorrelationDbModel>>
          builderFunction) {
    return builderFunction
        .apply(
            new MessageCorrelationDbModelBuilder()
                .correlationKey(correlationKey)
                .correlationTime(correlationTime)
                .flowNodeId(flowNodeId)
                .flowNodeInstanceKey(flowNodeInstanceKey)
                .historyCleanupDate(historyCleanupDate)
                .messageKey(messageKey)
                .messageName(messageName)
                .partitionId(partitionId)
                .processDefinitionId(processDefinitionId)
                .processDefinitionKey(processDefinitionKey)
                .processInstanceKey(processInstanceKey)
                .subscriptionKey(subscriptionKey)
                .tenantId(tenantId))
        .build();
  }

  public static class MessageCorrelationDbModelBuilder
      implements ObjectBuilder<MessageCorrelationDbModel> {

    private String correlationKey;
    private OffsetDateTime correlationTime;
    private String flowNodeId;
    private Long flowNodeInstanceKey;
    private OffsetDateTime historyCleanupDate;
    private Long messageKey;
    private String messageName;
    private int partitionId;
    private String processDefinitionId;
    private Long processDefinitionKey;
    private Long processInstanceKey;
    private Long subscriptionKey;
    private String tenantId;

    public MessageCorrelationDbModelBuilder() {}

    public MessageCorrelationDbModelBuilder correlationKey(final String correlationKey) {
      this.correlationKey = correlationKey;
      return this;
    }

    public MessageCorrelationDbModelBuilder correlationTime(final OffsetDateTime correlationTime) {
      this.correlationTime = correlationTime;
      return this;
    }

    public MessageCorrelationDbModelBuilder flowNodeId(final String flowNodeId) {
      this.flowNodeId = flowNodeId;
      return this;
    }

    public MessageCorrelationDbModelBuilder flowNodeInstanceKey(final Long flowNodeInstanceKey) {
      this.flowNodeInstanceKey = flowNodeInstanceKey;
      return this;
    }

    public MessageCorrelationDbModelBuilder historyCleanupDate(final OffsetDateTime historyCleanupDate) {
      this.historyCleanupDate = historyCleanupDate;
      return this;
    }

    public MessageCorrelationDbModelBuilder messageKey(final Long messageKey) {
      this.messageKey = messageKey;
      return this;
    }

    public MessageCorrelationDbModelBuilder messageName(final String messageName) {
      this.messageName = messageName;
      return this;
    }

    public MessageCorrelationDbModelBuilder partitionId(final int partitionId) {
      this.partitionId = partitionId;
      return this;
    }

    public MessageCorrelationDbModelBuilder processDefinitionId(final String processDefinitionId) {
      this.processDefinitionId = processDefinitionId;
      return this;
    }

    public MessageCorrelationDbModelBuilder processDefinitionKey(final Long processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return this;
    }

    public MessageCorrelationDbModelBuilder processInstanceKey(final Long processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
      return this;
    }

    public MessageCorrelationDbModelBuilder subscriptionKey(final Long subscriptionKey) {
      this.subscriptionKey = subscriptionKey;
      return this;
    }

    public MessageCorrelationDbModelBuilder tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    @Override
    public MessageCorrelationDbModel build() {
      return new MessageCorrelationDbModel(
          correlationKey,
          correlationTime,
          flowNodeId,
          flowNodeInstanceKey,
          historyCleanupDate,
          messageKey,
          messageName,
          partitionId,
          processDefinitionId,
          processDefinitionKey,
          processInstanceKey,
          subscriptionKey,
          tenantId);
    }
  }
}