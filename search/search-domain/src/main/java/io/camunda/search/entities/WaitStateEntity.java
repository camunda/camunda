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
    long processInstanceKey,
    long elementInstanceKey,
    String elementId,
    FlowNodeType elementType,
    @Nullable Long rootProcessInstanceKey,
    WaitStateDetails details) {

  public WaitStateEntity {
    Objects.requireNonNull(elementId, "elementId");
    Objects.requireNonNull(elementType, "elementType");
    Objects.requireNonNull(details, "details");
  }

  public static class Builder implements ObjectBuilder<WaitStateEntity> {
    private long processInstanceKey;
    private long elementInstanceKey;
    private @Nullable String elementId;
    private @Nullable FlowNodeType elementType;
    private @Nullable Long rootProcessInstanceKey;
    private @Nullable WaitStateDetails details;

    public Builder processInstanceKey(final long processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
      return this;
    }

    public Builder elementInstanceKey(final long elementInstanceKey) {
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

    public Builder rootProcessInstanceKey(final @Nullable Long rootProcessInstanceKey) {
      this.rootProcessInstanceKey = rootProcessInstanceKey;
      return this;
    }

    public Builder details(final WaitStateDetails details) {
      this.details = details;
      return this;
    }

    @SuppressWarnings("NullAway")
    @Override
    public WaitStateEntity build() {
      return new WaitStateEntity(
          processInstanceKey,
          elementInstanceKey,
          elementId,
          elementType,
          rootProcessInstanceKey,
          details);
    }
  }
}
