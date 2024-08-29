/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.search.sort;

import io.camunda.util.ObjectBuilder;
import java.util.List;

public record FlownodeInstanceSort() implements SortOption {

  @Override
  public List<FieldSorting> getFieldSortings() {
    return List.of();
  }

  public static final class Builder extends AbstractBuilder<FlownodeInstanceSort.Builder>
      implements ObjectBuilder<FlownodeInstanceSort> {

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public FlownodeInstanceSort build() {
      return new FlownodeInstanceSort();
    }
  }
}
