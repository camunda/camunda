/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.domain;

import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.FlowNodeInstanceFilter;
import io.camunda.util.ObjectBuilder;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public final class FlowNodeInstanceDbQuery {
  private final FlowNodeInstanceFilter filter;
  private final DbQuerySorting<FlowNodeInstanceEntity> sort;
  private final DbQueryPage page;
  private final String legacyId;
  private final String legacyProcessInstanceId;

  public FlowNodeInstanceDbQuery(
      FlowNodeInstanceFilter filter,
      DbQuerySorting<FlowNodeInstanceEntity> sort,
      DbQueryPage page,
      String legacyId,
      String legacyProcessInstanceId) {
    this.filter = filter;
    this.sort = sort;
    this.page = page;
    this.legacyId = legacyId;
    this.legacyProcessInstanceId = legacyProcessInstanceId;
  }

  public static FlowNodeInstanceDbQuery of(
      final Function<Builder, ObjectBuilder<FlowNodeInstanceDbQuery>> fn) {
    return fn.apply(new Builder()).build();
  }

  public FlowNodeInstanceFilter filter() {
    return filter;
  }

  public DbQuerySorting<FlowNodeInstanceEntity> sort() {
    return sort;
  }

  public DbQueryPage page() {
    return page;
  }

  public String legacyId() {
    return legacyId;
  }

  public String legacyProcessInstanceId() {
    return legacyProcessInstanceId;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj == null || obj.getClass() != this.getClass()) {
      return false;
    }
    final var that = (FlowNodeInstanceDbQuery) obj;
    return Objects.equals(this.filter, that.filter)
        && Objects.equals(this.sort, that.sort)
        && Objects.equals(this.page, that.page)
        && Objects.equals(this.legacyId, that.legacyId)
        && Objects.equals(this.legacyProcessInstanceId, that.legacyProcessInstanceId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(filter, sort, page, legacyId, legacyProcessInstanceId);
  }

  @Override
  public String toString() {
    return "FlowNodeInstanceDbQuery["
        + "filter="
        + filter
        + ", "
        + "sort="
        + sort
        + ", "
        + "page="
        + page
        + ", "
        + "legacyId="
        + legacyId
        + ", "
        + "legacyProcessInstanceId="
        + legacyProcessInstanceId
        + ']';
  }

  public static final class Builder implements ObjectBuilder<FlowNodeInstanceDbQuery> {

    private static final FlowNodeInstanceFilter EMPTY_FILTER =
        FilterBuilders.flowNodeInstance().build();

    private FlowNodeInstanceFilter filter;
    private DbQuerySorting<FlowNodeInstanceEntity> sort;
    private DbQueryPage page;
    private String legacyId;
    private String legacyProcessInstanceId;

    public Builder legacyId(String id) {
      legacyId = id;
      return this;
    }

    public Builder legacyProcessInstanceId(String id) {
      legacyProcessInstanceId = id;
      return this;
    }

    public Builder filter(final FlowNodeInstanceFilter value) {
      filter = value;
      return this;
    }

    public Builder sort(final DbQuerySorting<FlowNodeInstanceEntity> value) {
      sort = value;
      return this;
    }

    public Builder page(final DbQueryPage value) {
      page = value;
      return this;
    }

    public Builder filter(
        final Function<FlowNodeInstanceFilter.Builder, ObjectBuilder<FlowNodeInstanceFilter>> fn) {
      return filter(FilterBuilders.flowNodeInstance(fn));
    }

    public Builder sort(
        final Function<
                DbQuerySorting.Builder<FlowNodeInstanceEntity>,
                ObjectBuilder<DbQuerySorting<FlowNodeInstanceEntity>>>
            fn) {
      return sort(DbQuerySorting.of(fn));
    }

    @Override
    public FlowNodeInstanceDbQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, new DbQuerySorting<>(List.of()));
      return new FlowNodeInstanceDbQuery(filter, sort, page, legacyId, legacyProcessInstanceId);
    }
  }
}
