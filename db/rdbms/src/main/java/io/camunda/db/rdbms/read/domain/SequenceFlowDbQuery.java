/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.domain;

import io.camunda.search.entities.SequenceFlowEntity;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.SequenceFlowFilter;
import io.camunda.util.ObjectBuilder;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record SequenceFlowDbQuery(
    SequenceFlowFilter filter,
    List<String> authorizedResourceIds,
    List<String> authorizedTenantIds,
    DbQuerySorting<SequenceFlowEntity> sort,
    DbQueryPage page) {

  public static SequenceFlowDbQuery of(
      final Function<SequenceFlowDbQuery.Builder, ObjectBuilder<SequenceFlowDbQuery>> fn) {
    return fn.apply(new SequenceFlowDbQuery.Builder()).build();
  }

  public static final class Builder implements ObjectBuilder<SequenceFlowDbQuery> {
    private static final SequenceFlowFilter EMPTY_FILTER = FilterBuilders.sequenceFlow().build();

    private SequenceFlowFilter filter;
    private List<String> authorizedResourceIds = Collections.emptyList();
    private List<String> authorizedTenantIds = Collections.emptyList();
    private DbQuerySorting<SequenceFlowEntity> sort;
    private DbQueryPage page;

    public Builder filter(final SequenceFlowFilter value) {
      filter = value;
      return this;
    }

    public Builder authorizedResourceIds(final List<String> authorizedResourceIds) {
      this.authorizedResourceIds = authorizedResourceIds;
      return this;
    }

    public Builder authorizedTenantIds(final List<String> authorizedTenantIds) {
      this.authorizedTenantIds = authorizedTenantIds;
      return this;
    }

    public Builder sort(final DbQuerySorting<SequenceFlowEntity> value) {
      sort = value;
      return this;
    }

    public Builder page(final DbQueryPage value) {
      page = value;
      return this;
    }

    public Builder filter(
        final Function<SequenceFlowFilter.Builder, ObjectBuilder<SequenceFlowFilter>> fn) {
      return filter(FilterBuilders.sequenceFlow(fn));
    }

    public Builder sort(
        final Function<
                DbQuerySorting.Builder<SequenceFlowEntity>,
                ObjectBuilder<DbQuerySorting<SequenceFlowEntity>>>
            fn) {
      return sort(DbQuerySorting.of(fn));
    }

    @Override
    public SequenceFlowDbQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, new DbQuerySorting<>(List.of()));
      authorizedResourceIds = Objects.requireNonNullElse(authorizedResourceIds, List.of());
      authorizedTenantIds = Objects.requireNonNullElse(authorizedTenantIds, List.of());
      return new SequenceFlowDbQuery(
          filter, authorizedResourceIds, authorizedTenantIds, sort, page);
    }
  }
}
