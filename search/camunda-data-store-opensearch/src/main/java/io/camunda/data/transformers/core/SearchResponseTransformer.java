/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.transformers.core;

import io.camunda.data.clients.core.DataStoreSearchResponse;
import io.camunda.data.transformers.OpensearchTransformer;
import io.camunda.data.transformers.OpensearchTransformers;
import io.camunda.data.transformers.core.search.SearchHitTransformer;
import org.opensearch.client.opensearch.core.SearchResponse;

public class SearchResponseTransformer<T>
    extends OpensearchTransformer<SearchResponse<T>, DataStoreSearchResponse<T>> {

  public SearchResponseTransformer(final OpensearchTransformers mappers) {
    super(mappers);
  }

  @Override
  public DataStoreSearchResponse<T> apply(final SearchResponse<T> value) {
    final var hitTransformer = new SearchHitTransformer<T>(mappers);
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
