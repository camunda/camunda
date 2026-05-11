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
import java.util.Objects;
import org.jspecify.annotations.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CorrelatedMessageSubscriptionEntity(
    @Nullable String correlationKey,
    OffsetDateTime correlationTime,
    String flowNodeId,
    @Nullable Long flowNodeInstanceKey,
    Long messageKey,
    String messageName,
    Integer partitionId,
    String processDefinitionId,
    Long processDefinitionKey,
    Long processInstanceKey,
    @Nullable Long rootProcessInstanceKey,
    Long subscriptionKey,
    @Nullable MessageSubscriptionType subscriptionType,
    String tenantId)
    implements TenantOwnedEntity {

  public CorrelatedMessageSubscriptionEntity {
    Objects.requireNonNull(correlationTime, "correlationTime");
    Objects.requireNonNull(flowNodeId, "flowNodeId");
    Objects.requireNonNull(messageKey, "messageKey");
    Objects.requireNonNull(messageName, "messageName");
    Objects.requireNonNull(partitionId, "partitionId");
    Objects.requireNonNull(processDefinitionId, "processDefinitionId");
    Objects.requireNonNull(processDefinitionKey, "processDefinitionKey");
    Objects.requireNonNull(processInstanceKey, "processInstanceKey");
    Objects.requireNonNull(subscriptionKey, "subscriptionKey");
    Objects.requireNonNull(tenantId, "tenantId");
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private @Nullable String correlationKey;
    private @Nullable OffsetDateTime correlationTime;
    private @Nullable String flowNodeId;
    private @Nullable Long flowNodeInstanceKey;
    private @Nullable Long messageKey;
    private @Nullable String messageName;
    private @Nullable Integer partitionId;
    private @Nullable String processDefinitionId;
    private @Nullable Long processDefinitionKey;
    private @Nullable Long processInstanceKey;
    private @Nullable Long rootProcessInstanceKey;
    private @Nullable Long subscriptionKey;
    private @Nullable MessageSubscriptionType subscriptionType;
    private @Nullable String tenantId;

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

    @SuppressWarnings("NullAway")
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
