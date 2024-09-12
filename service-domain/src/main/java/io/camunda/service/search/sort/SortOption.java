/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.search.sort;

import java.util.ArrayList;
import java.util.List;

public interface SortOption {

  public List<FieldSorting> getFieldSortings();

  public abstract static class AbstractBuilder<T> {

    protected final List<FieldSorting> orderings = new ArrayList<>();
    protected FieldSorting currentOrdering;

    protected abstract T self();

    protected T addOrdering(final SortOrder value) {
      if (currentOrdering != null) {
        final var field = currentOrdering.field();
        final var newOrdering = new FieldSorting(field, value);
        orderings.add(newOrdering);
        currentOrdering = null;
      }
      // else if not set, then noop

      return self();
    }

    public T asc() {
      return addOrdering(SortOrder.ASC);
    }

    public T desc() {
      return addOrdering(SortOrder.DESC);
    }
  }

  public final record FieldSorting(String field, SortOrder order) {}
}
