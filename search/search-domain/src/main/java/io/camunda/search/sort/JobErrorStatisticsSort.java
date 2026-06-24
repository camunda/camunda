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

public record JobErrorStatisticsSort(List<FieldSorting> orderings) implements SortOption {

  @Override
  public List<FieldSorting> getFieldSortings() {
    return orderings;
  }

  public static JobErrorStatisticsSort of(
      final Function<JobErrorStatisticsSort.Builder, ObjectBuilder<JobErrorStatisticsSort>> fn) {
    return SortOptionBuilders.jobErrorStatistics(fn);
  }

  public static final class Builder extends AbstractBuilder<JobErrorStatisticsSort.Builder>
      implements ObjectBuilder<JobErrorStatisticsSort> {

    public Builder errorCode() {
      currentOrdering = new FieldSorting("errorCode", null);
      return this;
    }

    public Builder errorMessage() {
      currentOrdering = new FieldSorting("errorMessage", null);
      return this;
    }

    @Override
    protected JobErrorStatisticsSort.Builder self() {
      return this;
    }

    @Override
    public JobErrorStatisticsSort build() {
      return new JobErrorStatisticsSort(orderings);
    }
  }
}
