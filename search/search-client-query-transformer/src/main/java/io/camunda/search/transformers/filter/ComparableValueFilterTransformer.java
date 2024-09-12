/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.bool;
import static io.camunda.search.clients.query.SearchQueryBuilders.not;
import static io.camunda.search.clients.query.SearchQueryBuilders.range;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.query.SearchQueryBuilders;
import io.camunda.search.clients.types.TypedValue;
import io.camunda.service.search.filter.ComparableValueFilter;
import io.camunda.service.search.filter.FilterBase;
import io.camunda.search.transformers.filter.ComparableValueFilterTransformer.ComparableFieldFilter;
import java.util.Collections;

public class ComparableValueFilterTransformer implements FilterTransformer<ComparableFieldFilter> {

  @Override
  public SearchQuery toSearchQuery(final ComparableFieldFilter filter) {
    final var field = filter.field();
    final var filterParams = filter.filter();
    final SearchQuery valueQuery;

    if (filterParams.eq() != null) {
      valueQuery =
          bool()
              .must(Collections.singletonList(of(filterParams.eq(), field)))
              .build()
              .toSearchQuery();
    } else if (filterParams.neq() != null) {
      valueQuery = not(of(filterParams.neq(), field));
    } else {
      final var builder = range().field(field);

      if (filterParams.gt() != null) {
        builder.gt(filterParams.gt());
      }

      if (filterParams.gte() != null) {
        builder.gte(filterParams.gte());
      }

      if (filterParams.lt() != null) {
        builder.lt(filterParams.lt());
      }

      if (filterParams.lte() != null) {
        builder.lte(filterParams.lte());
      }
      valueQuery = builder.build().toSearchQuery();
    }

    return valueQuery;
  }

  private SearchQuery of(final Object value, final String field) {
    final var typedValue = TypedValue.toTypedValue(value);
    return SearchQueryBuilders.term().field(field).value(typedValue).build().toSearchQuery();
  }

  public record ComparableFieldFilter(String field, ComparableValueFilter filter)
      implements FilterBase {}
}
