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
import java.util.List;
import org.opensearch.client.opensearch.core.search.SourceFilter;

public final class SourceFilterTransformer
    extends OpensearchTransformer<SearchSourceFilter, SourceFilter> {

  public SourceFilterTransformer(final OpensearchTransformers transformers) {
    super(transformers);
  }

  @Override
  public SourceFilter apply(final SearchSourceFilter value) {
    final var builder = new SourceFilter.Builder();

    final List<String> includes = value.includes();
    if (includes != null && !includes.isEmpty()) {
      builder.includes(includes);
    }

    final List<String> excludes = value.excludes();
    if (excludes != null && !excludes.isEmpty()) {
      builder.excludes(excludes);
    }

    return builder.build();
  }
}
