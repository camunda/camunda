/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.query.search;

import io.camunda.service.query.filter.FilterBuilders;
import io.camunda.service.query.filter.ProcessInstanceFilter;
import io.camunda.service.query.sort.ProcessInstanceSort;
import io.camunda.service.query.sort.SortOptionBuilders;
import io.camunda.util.DataStoreObjectBuilder;
import java.util.function.Function;

public final class ProcessInstanceQuery
    extends SearchQueryBase<ProcessInstanceFilter, ProcessInstanceSort> {

  private ProcessInstanceQuery(final Builder builder) {
    super(builder.filter, builder.sort, builder.page);
  }

  public static ProcessInstanceQuery of(
      final Function<Builder, DataStoreObjectBuilder<ProcessInstanceQuery>> fn) {
    return fn.apply(new Builder()).build();
  }

  public static final class Builder
      extends SearchQueryBase.Builder<
          ProcessInstanceFilter, ProcessInstanceSort, ProcessInstanceQuery, Builder> {

    private ProcessInstanceFilter filter;

    public Builder filter(final ProcessInstanceFilter filter) {
      this.filter = filter;
      return self();
    }

    public Builder filter(
        final Function<ProcessInstanceFilter.Builder, DataStoreObjectBuilder<ProcessInstanceFilter>>
            fn) {
      return filter(FilterBuilders.processInstance(fn));
    }

    public Builder sort(
        final Function<ProcessInstanceSort.Builder, DataStoreObjectBuilder<ProcessInstanceSort>>
            fn) {
      return sort(SortOptionBuilders.processInstance(fn));
    }

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public ProcessInstanceQuery build() {
      if (filter == null) {
        // TODO: add general check to ensure that a filter is always set
        filter = ProcessInstanceFilter.EMPTY_FILTER;
      }
      return new ProcessInstanceQuery(this);
    }
  }
}
