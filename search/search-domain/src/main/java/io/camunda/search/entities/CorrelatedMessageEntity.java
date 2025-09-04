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
public record CorrelatedMessageEntity(
    String correlationKey,
    OffsetDateTime correlationTime,
    String elementId,
    Long elementInstanceKey,
    Long messageKey,
    String messageName,
    String processDefinitionId,
    Long processDefinitionKey,
    Long processInstanceKey,
    Long subscriptionKey,
    String tenantId)
    implements TenantOwnedEntity {

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private String correlationKey;
    private OffsetDateTime correlationTime;
    private String elementId;
    private Long elementInstanceKey;
    private Long messageKey;
    private String messageName;
    private String processDefinitionId;
    private Long processDefinitionKey;
    private Long processInstanceKey;
    private Long subscriptionKey;
    private String tenantId;

    public Builder correlationKey(final String correlationKey) {
      this.correlationKey = correlationKey;
      return this;
    }

    public Builder correlationTime(final OffsetDateTime correlationTime) {
      this.correlationTime = correlationTime;
      return this;
    }

    public Builder elementId(final String elementId) {
      this.elementId = elementId;
      return this;
    }

    public Builder elementInstanceKey(final Long elementInstanceKey) {
      this.elementInstanceKey = elementInstanceKey;
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

    public Builder subscriptionKey(final Long subscriptionKey) {
      this.subscriptionKey = subscriptionKey;
      return this;
    }

    public Builder tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    public CorrelatedMessageEntity build() {
      return new CorrelatedMessageEntity(
          correlationKey,
          correlationTime,
          elementId,
          elementInstanceKey,
          messageKey,
          messageName,
          processDefinitionId,
          processDefinitionKey,
          processInstanceKey,
          subscriptionKey,
          tenantId);
    }
  }
}