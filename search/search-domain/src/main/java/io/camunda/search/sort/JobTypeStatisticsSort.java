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

public record JobTypeStatisticsSort(List<FieldSorting> orderings) implements SortOption {

  @Override
  public List<FieldSorting> getFieldSortings() {
    return orderings;
  }

  public static JobTypeStatisticsSort of(
      final Function<JobTypeStatisticsSort.Builder, ObjectBuilder<JobTypeStatisticsSort>> fn) {
    return SortOptionBuilders.jobTypeStatistics(fn);
  }

  public static final class Builder extends AbstractBuilder<JobTypeStatisticsSort.Builder>
      implements ObjectBuilder<JobTypeStatisticsSort> {

    public Builder jobType() {
      currentOrdering = new FieldSorting("jobType", null);
      return this;
    }

    @Override
    protected JobTypeStatisticsSort.Builder self() {
      return this;
    }

    @Override
    public JobTypeStatisticsSort build() {
      return new JobTypeStatisticsSort(orderings);
    }
  }
}
