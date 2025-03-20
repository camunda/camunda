package io.camunda.db.rdbms.read.domain;

import io.camunda.search.entities.BatchOperationEntity;
import io.camunda.search.filter.BatchOperationFilter;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.util.ObjectBuilder;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record BatchOperationDbQuery(
    BatchOperationFilter filter, DbQuerySorting<BatchOperationEntity> sort, DbQueryPage page) {

  public static BatchOperationDbQuery of(
      final Function<BatchOperationDbQuery.Builder, ObjectBuilder<BatchOperationDbQuery>> fn) {
    return fn.apply(new BatchOperationDbQuery.Builder()).build();
  }

  public static final class Builder implements ObjectBuilder<BatchOperationDbQuery> {

    private static final BatchOperationFilter EMPTY_FILTER =
        FilterBuilders.batchOperation().build();

    private BatchOperationFilter filter;
    private DbQuerySorting<BatchOperationEntity> sort;
    private DbQueryPage page;

    public Builder filter(final BatchOperationFilter value) {
      filter = value;
      return this;
    }

    public Builder sort(final DbQuerySorting<BatchOperationEntity> value) {
      sort = value;
      return this;
    }

    public Builder page(final DbQueryPage value) {
      page = value;
      return this;
    }

    public Builder filter(
        final Function<BatchOperationFilter.Builder, ObjectBuilder<BatchOperationFilter>> fn) {
      return filter(FilterBuilders.batchOperation(fn));
    }

    public Builder sort(
        final Function<
                DbQuerySorting.Builder<BatchOperationEntity>,
                ObjectBuilder<DbQuerySorting<BatchOperationEntity>>>
            fn) {
      return sort(DbQuerySorting.of(fn));
    }

    @Override
    public BatchOperationDbQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, new DbQuerySorting<>(List.of()));
      return new BatchOperationDbQuery(filter, sort, page);
    }
  }
}
