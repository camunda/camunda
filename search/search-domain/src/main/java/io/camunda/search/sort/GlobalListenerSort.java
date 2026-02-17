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

public final record GlobalListenerSort(List<FieldSorting> orderings) implements SortOption {

  @Override
  public List<FieldSorting> getFieldSortings() {
    return orderings;
  }

  public static GlobalListenerSort of(
      final Function<Builder, ObjectBuilder<GlobalListenerSort>> fn) {
    return SortOptionBuilders.globalListener(fn);
  }

  public static final class Builder extends AbstractBuilder<Builder>
      implements ObjectBuilder<GlobalListenerSort> {

    public Builder id() {
      currentOrdering = new FieldSorting("id", null);
      return this;
    }

    public Builder listenerId() {
      currentOrdering = new FieldSorting("listenerId", null);
      return this;
    }

    public Builder type() {
      currentOrdering = new FieldSorting("type", null);
      return this;
    }

    public Builder afterNonGlobal() {
      currentOrdering = new FieldSorting("afterNonGlobal", null);
      return this;
    }

    public Builder priority() {
      currentOrdering = new FieldSorting("priority", null);
      return this;
    }

    public Builder source() {
      currentOrdering = new FieldSorting("source", null);
      return this;
    }

    public Builder listenerType() {
      currentOrdering = new FieldSorting("listenerType", null);
      return this;
    }

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public GlobalListenerSort build() {
      return new GlobalListenerSort(orderings);
    }
  }
}
