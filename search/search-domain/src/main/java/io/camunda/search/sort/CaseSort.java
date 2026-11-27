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
import org.jspecify.annotations.NullMarked;

@NullMarked
public record CaseSort(List<FieldSorting> orderings) implements SortOption {

  @Override
  public List<FieldSorting> getFieldSortings() {
    return orderings;
  }

  public static CaseSort of(final Function<CaseSort.Builder, ObjectBuilder<CaseSort>> fn) {
    return SortOptionBuilders.caseSort(fn);
  }

  public static final class Builder extends AbstractBuilder<Builder>
      implements ObjectBuilder<CaseSort> {

    public Builder caseKey() {
      currentOrdering = new FieldSorting("caseKey", null);
      return this;
    }

    public Builder processDefinitionId() {
      currentOrdering = new FieldSorting("processDefinitionId", null);
      return this;
    }

    public Builder businessId() {
      currentOrdering = new FieldSorting("businessId", null);
      return this;
    }

    public Builder title() {
      currentOrdering = new FieldSorting("title", null);
      return this;
    }

    public Builder state() {
      currentOrdering = new FieldSorting("state", null);
      return this;
    }

    public Builder startDate() {
      currentOrdering = new FieldSorting("startDate", null);
      return this;
    }

    public Builder endDate() {
      currentOrdering = new FieldSorting("endDate", null);
      return this;
    }

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public CaseSort build() {
      return new CaseSort(orderings);
    }
  }
}
