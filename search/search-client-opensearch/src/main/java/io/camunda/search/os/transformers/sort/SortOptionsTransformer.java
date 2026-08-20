/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.os.transformers.sort;

import io.camunda.search.clients.transformers.SearchTransfomer;
import io.camunda.search.os.transformers.OpensearchTransformer;
import io.camunda.search.os.transformers.OpensearchTransformers;
import io.camunda.search.sort.SearchFieldSort;
import io.camunda.search.sort.SearchSortOptions;
import org.opensearch.client.opensearch._types.FieldSort;
import org.opensearch.client.opensearch._types.SortOptions;

public final class SortOptionsTransformer
    extends OpensearchTransformer<SearchSortOptions, SortOptions> {

  public SortOptionsTransformer(final OpensearchTransformers transformers) {
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
