/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.sort;

import io.camunda.util.ObjectBuilder;
import java.util.List;
import java.util.function.Function;

public record AuditLogSort(List<FieldSorting> orderings) implements SortOption {
  @Override
  public List<FieldSorting> getFieldSortings() {
    return orderings;
  }

  public static AuditLogSort of(final Function<Builder, ObjectBuilder<AuditLogSort>> fn) {
    return SortOptionBuilders.auditLog(fn);
  }

  public static final class Builder extends AbstractBuilder<Builder>
      implements ObjectBuilder<AuditLogSort> {

    public Builder operationKey() {
      currentOrdering = new FieldSorting("operationKey", null);
      return this;
    }

    public Builder timestamp() {
      currentOrdering = new FieldSorting("timestamp", null);
      return this;
    }

    public Builder operationType() {
      currentOrdering = new FieldSorting("operationType", null);
      return this;
    }

    public Builder operationState() {
      currentOrdering = new FieldSorting("operationState", null);
      return this;
    }

    public Builder entityKey() {
      currentOrdering = new FieldSorting("entityKey", null);
      return this;
    }

    public Builder entityType() {
      currentOrdering = new FieldSorting("entityType", null);
      return this;
    }

    public Builder actorId() {
      currentOrdering = new FieldSorting("actorId", null);
      return this;
    }

    public Builder tenantId() {
      currentOrdering = new FieldSorting("tenantId", null);
      return this;
    }

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public Builder asc() {
      return addOrdering(SortOrder.ASC);
    }

    @Override
    public Builder desc() {
      return addOrdering(SortOrder.DESC);
    }

    @Override
    public AuditLogSort build() {
      return new AuditLogSort(orderings);
    }
  }
}
