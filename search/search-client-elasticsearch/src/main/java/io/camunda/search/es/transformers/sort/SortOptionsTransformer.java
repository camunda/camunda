/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.transformers.sort;

import co.elastic.clients.elasticsearch._types.FieldSort;
import co.elastic.clients.elasticsearch._types.SortOptions;
import io.camunda.search.clients.transformers.SearchTransfomer;
import io.camunda.search.es.transformers.ElasticsearchTransformer;
import io.camunda.search.es.transformers.ElasticsearchTransformers;
import io.camunda.search.sort.SearchFieldSort;
import io.camunda.search.sort.SearchSortOptions;

public final class SortOptionsTransformer
    extends ElasticsearchTransformer<SearchSortOptions, SortOptions> {

  public SortOptionsTransformer(final ElasticsearchTransformers transformers) {
    super(transformers);
  }

  @Override
  public SortOptions apply(final SearchSortOptions value) {
    final var transformer = getFieldSortTransformer();
    final var field = value.field();
    final var transformedField = transformer.apply(field);
    return new SortOptions.Builder().field(transformedField).build();
  }

  protected SearchTransfomer<SearchFieldSort, FieldSort> getFieldSortTransformer() {
    return transformers.getTransformer(SearchFieldSort.class);
  }
}
