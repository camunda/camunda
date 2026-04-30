/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.entities;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.util.ObjectBuilder;
import org.jspecify.annotations.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WaitingStateEntity(
    Long elementInstanceKey,
    Long processInstanceKey,
    String elementType,
    String details,
    String tenantId)
    implements TenantOwnedEntity {

  public WaitingStateEntity {
    requireNonNull(elementInstanceKey, "elementInstanceKey");
    requireNonNull(processInstanceKey, "processInstanceKey");
    requireNonNull(elementType, "elementType");
    requireNonNull(tenantId, "tenantId");
  }

  public static class Builder implements ObjectBuilder<WaitingStateEntity> {

    private @Nullable Long elementInstanceKey;
    private @Nullable Long processInstanceKey;
    private @Nullable String elementType;
    private @Nullable String details;
    private @Nullable String tenantId;

    public Builder elementInstanceKey(final Long elementInstanceKey) {
      this.elementInstanceKey = elementInstanceKey;
      return this;
    }

    public Builder processInstanceKey(final Long processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
      return this;
    }

    public Builder elementType(final String elementType) {
      this.elementType = elementType;
      return this;
    }

    public Builder details(final String details) {
      this.details = details;
      return this;
    }

    public Builder tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    @SuppressWarnings("NullAway")
    @Override
    public WaitingStateEntity build() {
      return new WaitingStateEntity(
          elementInstanceKey, processInstanceKey, elementType, details, tenantId);
    }
  }
}
