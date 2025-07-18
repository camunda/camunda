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

public record BatchOperationItemSort(List<FieldSorting> orderings) implements SortOption {

  @Override
  public List<FieldSorting> getFieldSortings() {
    return orderings;
  }

  public static BatchOperationItemSort of(
      final Function<Builder, ObjectBuilder<BatchOperationItemSort>> fn) {
    return SortOptionBuilders.batchOperationItem(fn);
  }

  public static final class Builder extends AbstractBuilder<BatchOperationItemSort.Builder>
      implements ObjectBuilder<BatchOperationItemSort> {

    public Builder batchOperationKey() {
      currentOrdering = new FieldSorting("batchOperationKey", null);
      return this;
    }

    public Builder state() {
      currentOrdering = new FieldSorting("state", null);
      return this;
    }

    public Builder itemKey() {
      currentOrdering = new FieldSorting("itemKey", null);
      return this;
    }

    public Builder processInstanceKey() {
      currentOrdering = new FieldSorting("processInstanceKey", null);
      return this;
    }

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public BatchOperationItemSort build() {
      return new BatchOperationItemSort(orderings);
    }
  }
}
