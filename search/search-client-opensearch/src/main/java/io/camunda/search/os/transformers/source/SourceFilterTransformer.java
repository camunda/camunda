/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.os.transformers.source;

import io.camunda.search.clients.source.SearchSourceFilter;
import io.camunda.search.os.transformers.OpensearchTransformer;
import io.camunda.search.os.transformers.OpensearchTransformers;
import org.opensearch.client.opensearch.core.search.SourceFilter;

public final class SourceFilterTransformer
    extends OpensearchTransformer<SearchSourceFilter, SourceFilter> {

  public SourceFilterTransformer(final OpensearchTransformers transformers) {
    super(transformers);
  }

  @Override
  public SourceFilter apply(final SearchSourceFilter value) {
    final var builder = new SourceFilter.Builder();
    if (value.includes() != null) {
      builder.includes(value.includes());
    }
    if (value.excludes() != null) {
      builder.excludes(value.excludes());
    }
    return builder.build();
  }
}
