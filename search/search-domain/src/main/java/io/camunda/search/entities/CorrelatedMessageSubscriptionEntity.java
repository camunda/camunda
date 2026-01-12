/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.OffsetDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CorrelatedMessageSubscriptionEntity(
    String correlationKey,
    OffsetDateTime correlationTime,
    String flowNodeId,
    Long flowNodeInstanceKey,
    Long messageKey,
    String messageName,
    Integer partitionId,
    String processDefinitionId,
    Long processDefinitionKey,
    Long processInstanceKey,
    Long rootProcessInstanceKey,
    Long subscriptionKey,
    MessageSubscriptionType subscriptionType,
    String tenantId)
    implements TenantOwnedEntity {
  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private String correlationKey;
    private OffsetDateTime correlationTime;
    private String flowNodeId;
    private Long flowNodeInstanceKey;
    private Long messageKey;
    private String messageName;
    private Integer partitionId;
    private String processDefinitionId;
    private Long processDefinitionKey;
    private Long processInstanceKey;
    private Long rootProcessInstanceKey;
    private Long subscriptionKey;
    private MessageSubscriptionType subscriptionType;
    private String tenantId;

    public Builder correlationKey(final String correlationKey) {
      this.correlationKey = correlationKey;
      return this;
    }

    public Builder correlationTime(final OffsetDateTime correlationTime) {
      this.correlationTime = correlationTime;
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

    public Builder messageKey(final Long messageKey) {
      this.messageKey = messageKey;
      return this;
    }

    public Builder messageName(final String messageName) {
      this.messageName = messageName;
      return this;
    }

    public Builder partitionId(final Integer partitionId) {
      this.partitionId = partitionId;
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

    public Builder subscriptionKey(final Long subscriptionKey) {
      this.subscriptionKey = subscriptionKey;
      return this;
    }

    public Builder subscriptionType(final MessageSubscriptionType subscriptionType) {
      this.subscriptionType = subscriptionType;
      return this;
    }

    public Builder tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    public CorrelatedMessageSubscriptionEntity build() {
      return new CorrelatedMessageSubscriptionEntity(
          correlationKey,
          correlationTime,
          flowNodeId,
          flowNodeInstanceKey,
          messageKey,
          messageName,
          partitionId,
          processDefinitionId,
          processDefinitionKey,
          processInstanceKey,
          rootProcessInstanceKey,
          subscriptionKey,
          subscriptionType,
          tenantId);
    }
  }

  public enum MessageSubscriptionType {
    PROCESS_EVENT,
    START_EVENT
  }
}
