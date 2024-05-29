/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.transformers.search;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch.core.search.Hit;
import io.camunda.data.clients.core.search.DataStoreSearchHit;
import io.camunda.data.transformers.ElasticsearchTransformer;
import io.camunda.data.transformers.ElasticsearchTransformers;
import java.util.List;

public class SearchHitTransformer<T>
    extends ElasticsearchTransformer<Hit<T>, DataStoreSearchHit<T>> {

  public SearchHitTransformer(ElasticsearchTransformers transformers) {
    super(transformers);
  }

  @Override
  public DataStoreSearchHit<T> apply(Hit<T> value) {
    final Object[] sortValues = toArray(value.sort());

    return new DataStoreSearchHit.Builder<T>()
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
