/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.transformers.search;

import io.camunda.data.clients.core.search.DataStoreSearchHit;
import io.camunda.data.transformers.OpensearchTransformer;
import io.camunda.data.transformers.OpensearchTransformers;
import java.util.List;
import org.opensearch.client.opensearch.core.search.Hit;

public class SearchHitTransformer<T> extends OpensearchTransformer<Hit<T>, DataStoreSearchHit<T>> {

  public SearchHitTransformer(OpensearchTransformers transformers) {
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

  private Object[] toArray(final List<String> values) {
    if (values != null && !values.isEmpty()) {
      return values.toArray();
    } else {
      return null;
    }
  }
}
