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

public record WaitStateSort(List<FieldSorting> orderings) implements SortOption {

  @Override
  public List<FieldSorting> getFieldSortings() {
    return orderings;
  }

  public static WaitStateSort of(
      final Function<WaitStateSort.Builder, ObjectBuilder<WaitStateSort>> fn) {
    return SortOptionBuilders.waitState(fn);
  }

  public static final class Builder extends AbstractBuilder<Builder>
      implements ObjectBuilder<WaitStateSort> {

    public Builder elementInstanceKey() {
      currentOrdering = new FieldSorting("elementInstanceKey", null);
      return this;
    }

    public Builder processInstanceKey() {
      currentOrdering = new FieldSorting("processInstanceKey", null);
      return this;
    }

    public Builder rootProcessInstanceKey() {
      currentOrdering = new FieldSorting("rootProcessInstanceKey", null);
      return this;
    }

    public Builder elementId() {
      currentOrdering = new FieldSorting("elementId", null);
      return this;
    }

    @Override
    public WaitStateSort build() {
      return new WaitStateSort(orderings);
    }

    @Override
    protected Builder self() {
      return this;
    }
  }
}
