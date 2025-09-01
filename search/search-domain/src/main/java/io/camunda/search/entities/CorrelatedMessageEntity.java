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
    Long messageKey,
    String messageName,
    String correlationKey,
    Long processInstanceKey,
    Long flowNodeInstanceKey,
    String startEventId,
    String bpmnProcessId,
    String variables,
    String tenantId,
    OffsetDateTime dateTime)
    implements TenantOwnedEntity {

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private Long messageKey;
    private String messageName;
    private String correlationKey;
    private Long processInstanceKey;
    private Long flowNodeInstanceKey;
    private String startEventId;
    private String bpmnProcessId;
    private String variables;
    private String tenantId;
    private OffsetDateTime dateTime;

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

    public CorrelatedMessageEntity build() {
      return new CorrelatedMessageEntity(
          messageKey,
          messageName,
          correlationKey,
          processInstanceKey,
          flowNodeInstanceKey,
          startEventId,
          bpmnProcessId,
          variables,
          tenantId,
          dateTime);
    }
  }
}