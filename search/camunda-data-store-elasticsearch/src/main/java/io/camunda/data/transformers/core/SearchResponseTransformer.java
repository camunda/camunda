/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.transformers.core;

import co.elastic.clients.elasticsearch.core.SearchResponse;
import io.camunda.data.clients.core.DataStoreSearchResponse;
import io.camunda.data.transformers.ElasticsearchTransformer;
import io.camunda.data.transformers.ElasticsearchTransformers;
import io.camunda.data.transformers.core.search.SearchHitTransformer;

public class SearchResponseTransformer<T>
    extends ElasticsearchTransformer<SearchResponse<T>, DataStoreSearchResponse<T>> {

  public SearchResponseTransformer(final ElasticsearchTransformers transformers) {
    super(transformers);
  }

  @Override
  public DataStoreSearchResponse<T> apply(final SearchResponse<T> value) {
    final var hitTransformer = new SearchHitTransformer<T>(transformers);
    final var totalHits = value.hits().total().value();
    final var scrollId = value.scrollId();
    final var hits = value.hits().hits().stream().map(hitTransformer::apply).toList();

    return new DataStoreSearchResponse.Builder<T>()
        .totalHits(totalHits)
        .scrollId(scrollId)
        .hits(hits)
        .build();
  }
}
