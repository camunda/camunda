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

public record JobTimeSeriesStatisticsSort(List<FieldSorting> orderings) implements SortOption {

  @Override
  public List<FieldSorting> getFieldSortings() {
    return orderings;
  }

  public static JobTimeSeriesStatisticsSort of(
      final Function<
              JobTimeSeriesStatisticsSort.Builder, ObjectBuilder<JobTimeSeriesStatisticsSort>>
          fn) {
    return SortOptionBuilders.jobTimeSeriesStatistics(fn);
  }

  public static final class Builder extends AbstractBuilder<JobTimeSeriesStatisticsSort.Builder>
      implements ObjectBuilder<JobTimeSeriesStatisticsSort> {

    public Builder time() {
      currentOrdering = new FieldSorting("time", null);
      return this;
    }

    @Override
    protected JobTimeSeriesStatisticsSort.Builder self() {
      return this;
    }

    @Override
    public JobTimeSeriesStatisticsSort build() {
      return new JobTimeSeriesStatisticsSort(orderings);
    }
  }
}
