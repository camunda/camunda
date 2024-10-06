/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.transformers.search;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch.core.search.Hit;
import io.camunda.search.clients.core.SearchQueryHit;
import io.camunda.search.es.transformers.ElasticsearchTransformer;
import io.camunda.search.es.transformers.ElasticsearchTransformers;
import java.util.List;

public final class SearchQueryHitTransformer<T>
    extends ElasticsearchTransformer<Hit<T>, SearchQueryHit<T>> {

  public SearchQueryHitTransformer(final ElasticsearchTransformers transformers) {
    super(transformers);
  }

  @Override
  public SearchQueryHit<T> apply(Hit<T> value) {
    final var sortValues = toArray(value.sort());
    return new SearchQueryHit.Builder<T>()
        .id(value.id())
        .index(value.index())
        .shard(value.shard())
        .routing(value.routing())
        .seqNo(value.seqNo())
        .version(value.version())
        .source(value.source())
        .sortValues(sortValues)
        .build();
  }

  private Object[] toArray(final List<FieldValue> values) {
    if (values != null && !values.isEmpty()) {
      return values.stream().map(FieldValue::_get).toArray();
    } else {
      return null;
    }
  }
}
