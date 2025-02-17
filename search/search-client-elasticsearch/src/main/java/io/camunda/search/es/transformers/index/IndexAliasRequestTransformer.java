/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.transformers.index;

import co.elastic.clients.elasticsearch.indices.GetAliasRequest;
import io.camunda.search.clients.index.IndexAliasRequest;
import io.camunda.search.es.transformers.ElasticsearchTransformer;
import io.camunda.search.es.transformers.ElasticsearchTransformers;

public class IndexAliasRequestTransformer
    extends ElasticsearchTransformer<IndexAliasRequest, GetAliasRequest> {

  public IndexAliasRequestTransformer(final ElasticsearchTransformers transformers) {
    super(transformers);
  }

  @Override
  public GetAliasRequest apply(final IndexAliasRequest value) {
    return GetAliasRequest.of(b -> b.index(value.index()).name(value.name()));
  }
}
