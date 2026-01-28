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
public record MessageSubscriptionEntity(
    Long messageSubscriptionKey,
    String processDefinitionId,
    Long processDefinitionKey,
    Long processInstanceKey,
    Long rootProcessInstanceKey,
    String flowNodeId,
    Long flowNodeInstanceKey,
    MessageSubscriptionState messageSubscriptionState,
    OffsetDateTime dateTime,
    String messageName,
    String correlationKey,
    String tenantId)
    implements TenantOwnedEntity {
  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
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

    public MessageSubscriptionEntity build() {
      return new MessageSubscriptionEntity(
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
          tenantId);
    }
  }

  public enum MessageSubscriptionState {
    CORRELATED,
    CREATED,
    DELETED,
    MIGRATED
  }
}
