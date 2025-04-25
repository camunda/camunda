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

public record GroupSort(List<FieldSorting> orderings) implements SortOption {
  @Override
  public List<FieldSorting> getFieldSortings() {
    return orderings;
  }

  public static GroupSort of(final Function<Builder, ObjectBuilder<GroupSort>> fn) {
    return SortOptionBuilders.group(fn);
  }

  public static final class Builder extends AbstractBuilder<Builder>
      implements ObjectBuilder<GroupSort> {

    public Builder groupKey() {
      currentOrdering = new FieldSorting("groupKey", null);
      return this;
    }

    public Builder groupId() {
      currentOrdering = new FieldSorting("groupId", null);
      return this;
    }

    public Builder name() {
      currentOrdering = new FieldSorting("name", null);
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
    public GroupSort build() {
      return new GroupSort(orderings);
    }
  }
}
