/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.query;

import io.camunda.search.clients.core.SearchQueryHit;
import io.camunda.search.clients.core.SearchQueryResponse;
import io.camunda.search.clients.transformers.ServiceTransformer;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.SearchQueryResult.Builder;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public final class SearchQueryResultTransformer<T>
    implements ServiceTransformer<SearchQueryResponse<T>, SearchQueryResult<T>> {

  @Override
  public SearchQueryResult<T> apply(final SearchQueryResponse<T> value) {
    final var hits = value.hits();
    final var items = of(hits);
    final var size = hits.size();
    final Object[] sortValues;
    if (size > 0) {
      final var lastItem = hits.get(size - 1);
      sortValues = lastItem.sortValues();
    } else {
      sortValues = null;
    }

    return new Builder<T>().total(value.totalHits()).sortValues(sortValues).items(items).build();
  }

  private List<T> of(final List<SearchQueryHit<T>> values) {
    if (values != null) {
      return values.stream().map(SearchQueryHit::source).collect(Collectors.toList());
    }
    return Collections.emptyList();
  }
}
