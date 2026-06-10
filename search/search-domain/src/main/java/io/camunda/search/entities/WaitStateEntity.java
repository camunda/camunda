/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WaitStateEntity(
    Long processInstanceKey,
    Long elementInstanceKey,
    String elementId,
    FlowNodeType elementType,
    @Nullable Long rootProcessInstanceKey,
    String bpmnProcessId,
    WaitStateDetails details,
    String tenantId) {

  public WaitStateEntity {
    Objects.requireNonNull(processInstanceKey, "processInstanceKey");
    Objects.requireNonNull(elementInstanceKey, "elementInstanceKey");
    Objects.requireNonNull(elementId, "elementId");
    Objects.requireNonNull(elementType, "elementType");
    Objects.requireNonNull(bpmnProcessId, "bpmnProcessId");
    Objects.requireNonNull(tenantId, "tenantId");
    Objects.requireNonNull(details, "details");
  }

  public static class Builder implements ObjectBuilder<WaitStateEntity> {
    private @Nullable Long processInstanceKey;
    private @Nullable Long elementInstanceKey;
    private @Nullable String elementId;
    private @Nullable FlowNodeType elementType;
    private @Nullable Long rootProcessInstanceKey;
    private @Nullable String bpmnProcessId;
    private @Nullable WaitStateDetails details;
    private @Nullable String tenantId;

    public Builder processInstanceKey(final Long processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
      return this;
    }

    public Builder elementInstanceKey(final Long elementInstanceKey) {
      this.elementInstanceKey = elementInstanceKey;
      return this;
    }

    public Builder elementId(final String elementId) {
      this.elementId = elementId;
      return this;
    }

    public Builder elementType(final FlowNodeType elementType) {
      this.elementType = elementType;
      return this;
    }

    public Builder rootProcessInstanceKey(final Long rootProcessInstanceKey) {
      this.rootProcessInstanceKey = rootProcessInstanceKey;
      return this;
    }

    public Builder bpmnProcessId(final String bpmnProcessId) {
      this.bpmnProcessId = bpmnProcessId;
      return this;
    }

    public Builder details(final WaitStateDetails details) {
      this.details = details;
      return this;
    }

    public Builder tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    @Override
    public WaitStateEntity build() {
      return new WaitStateEntity(
          Objects.requireNonNull(processInstanceKey, "processInstanceKey"),
          Objects.requireNonNull(elementInstanceKey, "elementInstanceKey"),
          Objects.requireNonNull(elementId, "elementId"),
          Objects.requireNonNull(elementType, "elementType"),
          rootProcessInstanceKey,
          Objects.requireNonNull(bpmnProcessId, "bpmnProcessId"),
          Objects.requireNonNull(details, "details"),
          Objects.requireNonNull(tenantId, "tenantId"));
    }
  }
}
