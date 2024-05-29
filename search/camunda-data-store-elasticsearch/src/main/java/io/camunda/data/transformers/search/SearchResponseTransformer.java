/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.transformers.search;

import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import io.camunda.data.clients.core.DataStoreSearchResponse;
import io.camunda.data.clients.core.search.DataStoreSearchHit;
import io.camunda.data.transformers.ElasticsearchTransformer;
import io.camunda.data.transformers.ElasticsearchTransformers;
import java.util.ArrayList;
import java.util.List;

public class SearchResponseTransformer<T>
    extends ElasticsearchTransformer<SearchResponse<T>, DataStoreSearchResponse<T>> {

  public SearchResponseTransformer(final ElasticsearchTransformers transformers) {
    super(transformers);
  }

  @Override
  public DataStoreSearchResponse<T> apply(final SearchResponse<T> value) {
    final var hits = value.hits();
    final var scrollId = value.scrollId();

    final var total = hits.total();
    final long totalHits = of(total);

    final var sourceHits = hits.hits();
    final List<DataStoreSearchHit<T>> transformedHits = of(sourceHits);

    return new DataStoreSearchResponse.Builder<T>()
        .totalHits(totalHits)
        .scrollId(scrollId)
        .hits(transformedHits)
        .build();
  }

  private List<DataStoreSearchHit<T>> of(final List<Hit<T>> hits) {
    if (hits != null) {
      final var hitTransformer = new SearchHitTransformer<T>(transformers);
      return hits.stream().map(hitTransformer::apply).toList();
    }
    return new ArrayList<>();
  }

  private long of(final TotalHits totalHits) {
    if (totalHits != null) {
      return totalHits.value();
    }
    return 0;
  }
}
