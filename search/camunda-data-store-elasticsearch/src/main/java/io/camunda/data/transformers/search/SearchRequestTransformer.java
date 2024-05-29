/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.transformers.search;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.json.JsonData;
import io.camunda.data.clients.core.DataStoreSearchRequest;
import io.camunda.data.clients.sort.DataStoreSortOptions;
import io.camunda.data.transformers.ElasticsearchTransformer;
import io.camunda.data.transformers.ElasticsearchTransformers;
import java.util.Arrays;
import java.util.List;

public class SearchRequestTransformer
    extends ElasticsearchTransformer<DataStoreSearchRequest, SearchRequest> {

  public SearchRequestTransformer(final ElasticsearchTransformers transformers) {
    super(transformers);
  }

  @Override
  public SearchRequest apply(final DataStoreSearchRequest value) {
    final var sort = value.sort();
    final var searchAfter = value.searchAfter();
    final var dataStoreQuery = value.query();

    final var builder =
        new SearchRequest.Builder().index(value.index()).from(value.from()).size(value.size());

    if (dataStoreQuery != null) {
      final var queryTransformer = getQueryTransformer();
      final var transformedQuery = queryTransformer.apply(dataStoreQuery);
      builder.query(transformedQuery);
    }

    if (sort != null && !sort.isEmpty()) {
      builder.sort(of(sort));
    }

    if (searchAfter != null && searchAfter.length > 0) {
      builder.searchAfter(of(searchAfter));
    }

    return builder.build();
  }

  private List<SortOptions> of(final List<DataStoreSortOptions> values) {
    final var sortTransformer = getSortOptionsTransformer();
    return values.stream().map(sortTransformer::apply).toList();
  }

  private List<FieldValue> of(final Object[] values) {
    return Arrays.asList(values).stream().map(JsonData::of).map(FieldValue::of).toList();
  }
}
