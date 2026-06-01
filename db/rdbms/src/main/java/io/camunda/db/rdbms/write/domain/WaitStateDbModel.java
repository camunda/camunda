/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import io.camunda.util.ObjectBuilder;

/**
 * Persistent representation of a single waiting-state element instance.
 *
 * <p>The {@code details} field holds the wait-state-specific attributes already serialized to JSON
 * (stored as a CLOB). It is intentionally kept as an opaque string here so it can be deserialized
 * into the matching {@code WaitStateDetails} type when read back, based on {@code waitStateType}.
 */
public record WaitStateDbModel(
    Long waitStateKey,
    Long rootProcessInstanceKey,
    Long processInstanceKey,
    Long elementInstanceKey,
    String elementId,
    String elementType,
    String waitStateType,
    String details,
    String tenantId,
    Integer partitionId) {

  public static class Builder implements ObjectBuilder<WaitStateDbModel> {

    private Long waitStateKey;
    private Long rootProcessInstanceKey;
    private Long processInstanceKey;
    private Long elementInstanceKey;
    private String elementId;
    private String elementType;
    private String waitStateType;
    private String details;
    private String tenantId;
    private Integer partitionId;

    public Builder waitStateKey(final Long waitStateKey) {
      this.waitStateKey = waitStateKey;
      return this;
    }

    public Builder rootProcessInstanceKey(final Long rootProcessInstanceKey) {
      this.rootProcessInstanceKey = rootProcessInstanceKey;
      return this;
    }

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

    public Builder elementType(final String elementType) {
      this.elementType = elementType;
      return this;
    }

    public Builder waitStateType(final String waitStateType) {
      this.waitStateType = waitStateType;
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

    public Builder partitionId(final Integer partitionId) {
      this.partitionId = partitionId;
      return this;
    }

    @Override
    public WaitStateDbModel build() {
      return new WaitStateDbModel(
          waitStateKey,
          rootProcessInstanceKey,
          processInstanceKey,
          elementInstanceKey,
          elementId,
          elementType,
          waitStateType,
          details,
          tenantId,
          partitionId);
    }
  }
}
