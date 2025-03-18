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

public record BatchOperationSort(List<FieldSorting> orderings) implements SortOption {

  @Override
  public List<FieldSorting> getFieldSortings() {
    return orderings;
  }

  public static BatchOperationSort of(
      final Function<Builder, ObjectBuilder<BatchOperationSort>> fn) {
    return SortOptionBuilders.batchOperationSort(fn);
  }

  public static final class Builder extends AbstractBuilder<BatchOperationSort.Builder>
      implements ObjectBuilder<BatchOperationSort> {

    public Builder batchOperationKey() {
      currentOrdering = new FieldSorting("batchOperationKey", null);
      return this;
    }

    public Builder state() {
      currentOrdering = new FieldSorting("state", null);
      return this;
    }

    public Builder operationType() {
      currentOrdering = new FieldSorting("operationType", null);
      return this;
    }

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public BatchOperationSort build() {
      return new BatchOperationSort(orderings);
    }
  }
}
