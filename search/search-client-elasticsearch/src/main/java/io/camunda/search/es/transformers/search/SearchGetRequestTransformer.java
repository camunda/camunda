/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.transformers.search;

import co.elastic.clients.elasticsearch.core.GetRequest;
import io.camunda.search.clients.core.SearchGetRequest;
import io.camunda.search.es.transformers.ElasticsearchTransformer;
import io.camunda.search.es.transformers.ElasticsearchTransformers;

public final class SearchGetRequestTransformer
    extends ElasticsearchTransformer<SearchGetRequest, GetRequest> {

  public SearchGetRequestTransformer(final ElasticsearchTransformers transformers) {
    super(transformers);
  }

  @Override
  public GetRequest apply(final SearchGetRequest value) {
    final var builder = new GetRequest.Builder();

    builder.id(value.id());
    builder.index(value.index());
    builder.routing(value.routing());

    final var excludes = value.sourceExcludes();
    if (excludes != null && !excludes.isEmpty()) {
      builder.sourceExcludes(excludes);
    }
    return builder.build();
  }
}
