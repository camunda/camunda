/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.transformers.source;

import co.elastic.clients.elasticsearch.core.search.SourceConfig;
import io.camunda.search.clients.source.SearchSourceConfig;
import io.camunda.search.es.transformers.ElasticsearchTransformer;
import io.camunda.search.es.transformers.ElasticsearchTransformers;

public final class SourceConfigTransformer
    extends ElasticsearchTransformer<SearchSourceConfig, SourceConfig> {

  public SourceConfigTransformer(final ElasticsearchTransformers transformers) {
    super(transformers);
  }

  @Override
  public SourceConfig apply(final SearchSourceConfig value) {
    final var builder = new SourceConfig.Builder();
    if (value.sourceFilter() != null) {
      final var filterTransformer = getSourceFilterTransformer();
      builder.filter(filterTransformer.apply(value.sourceFilter()));
    }

    return builder.build();
  }
}
