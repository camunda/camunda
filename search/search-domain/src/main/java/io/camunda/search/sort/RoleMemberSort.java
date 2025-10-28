/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.sort;

import io.camunda.search.sort.RoleSort.Builder;
import io.camunda.util.ObjectBuilder;
import java.util.List;
import java.util.function.Function;

public record RoleMemberSort(List<FieldSorting> orderings) implements SortOption {

  @Override
  public List<FieldSorting> getFieldSortings() {
    return orderings;
  }

  public static RoleMemberSort of(final Function<Builder, ObjectBuilder<RoleMemberSort>> fn) {
    return SortOptionBuilders.roleMember(fn);
  }

  public static final class Builder extends AbstractBuilder<Builder>
      implements ObjectBuilder<RoleMemberSort> {

    public Builder id() {
      currentOrdering = new FieldSorting("id", null);
      return this;
    }

    public Builder entityType() {
      currentOrdering = new FieldSorting("entityType", null);
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
    public RoleMemberSort build() {
      return new RoleMemberSort(orderings);
    }
  }
}
