/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.transformers.core;

import io.camunda.data.clients.core.DataStoreSearchRequest;
import io.camunda.data.clients.sort.DataStoreSortOptions;
import io.camunda.data.mappers.DataStoreTransformer;
import io.camunda.data.transformers.OpensearchTransformer;
import io.camunda.data.transformers.OpensearchTransformers;
import java.util.Arrays;
import java.util.List;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch.core.SearchRequest;

public class SearchRequestTransformer
    extends OpensearchTransformer<DataStoreSearchRequest, SearchRequest> {

  protected final DataStoreTransformer<DataStoreSortOptions, SortOptions> sortOptionsTransformer;

  public SearchRequestTransformer(final OpensearchTransformers mappers) {
    super(mappers);
    sortOptionsTransformer = mappers.getMapper(DataStoreSortOptions.class);
  }

  @Override
  public SearchRequest apply(final DataStoreSearchRequest value) {
    final var builder =
        new SearchRequest.Builder()
            .index(value.index())
            .query(queryTransformer.apply(value.query()))
            .from(value.from())
            .size(value.size());

    final var sort = value.sort();
    if (sort != null && !sort.isEmpty()) {
      builder.sort(of(sort));
    }

    final var searchAfter = value.searchAfter();
    if (searchAfter != null && searchAfter.length > 0) {
      builder.searchAfter(of(searchAfter));
    }

    return builder.build();
  }

  private List<SortOptions> of(final List<DataStoreSortOptions> values) {
    return values.stream().map(sortOptionsTransformer::apply).toList();
  }

  private List<String> of(final Object[] values) {
    return Arrays.asList(values).stream().map(Object::toString).toList();
  }
}
