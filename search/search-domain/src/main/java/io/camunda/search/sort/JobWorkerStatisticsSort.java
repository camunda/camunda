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

public record JobWorkerStatisticsSort(List<FieldSorting> orderings) implements SortOption {

  @Override
  public List<FieldSorting> getFieldSortings() {
    return orderings;
  }

  public static JobWorkerStatisticsSort of(
      final Function<JobWorkerStatisticsSort.Builder, ObjectBuilder<JobWorkerStatisticsSort>> fn) {
    return SortOptionBuilders.jobWorkerStatistics(fn);
  }

  public static final class Builder extends AbstractBuilder<JobWorkerStatisticsSort.Builder>
      implements ObjectBuilder<JobWorkerStatisticsSort> {

    public Builder worker() {
      currentOrdering = new FieldSorting("worker", null);
      return this;
    }

    @Override
    protected JobWorkerStatisticsSort.Builder self() {
      return this;
    }

    @Override
    public JobWorkerStatisticsSort build() {
      return new JobWorkerStatisticsSort(orderings);
    }
  }
}
