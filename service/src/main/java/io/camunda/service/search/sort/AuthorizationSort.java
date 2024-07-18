/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.search.sort;

import io.camunda.search.clients.sort.SortOrder;
import io.camunda.util.ObjectBuilder;
import java.util.List;
import java.util.function.Function;

public record AuthorizationSort(List<FieldSorting> fieldSortingList) implements SortOption {

  @Override
  public List<FieldSorting> getFieldSortings() {
    return fieldSortingList;
  }

  public static AuthorizationSort of(final Function<Builder, ObjectBuilder<AuthorizationSort>> fn) {
    return SortOptionBuilders.authorization(fn);
  }

  public static final class Builder extends AbstractBuilder<Builder>
      implements ObjectBuilder<AuthorizationSort> {

    public Builder authorizationKey() {
      currentOrdering = new FieldSorting("authorizationKey", null);
      return this;
    }

    public Builder asc() {
      return addOrdering(SortOrder.ASC);
    }

    public Builder desc() {
      return addOrdering(SortOrder.DESC);
    }

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public AuthorizationSort build() {
      return new AuthorizationSort(orderings);
    }
  }
}
