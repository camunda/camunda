/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.transformers.sort;

import co.elastic.clients.elasticsearch._types.FieldSort;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOptionsBuilders;
import co.elastic.clients.elasticsearch._types.SortOrder;
import io.camunda.search.es.transformers.ElasticsearchTransformer;
import io.camunda.search.es.transformers.ElasticsearchTransformers;
import io.camunda.search.sort.SearchFieldSort;

public final class FieldSortTransformer
    extends ElasticsearchTransformer<SearchFieldSort, FieldSort> {

  public FieldSortTransformer(final ElasticsearchTransformers transformers) {
    super(transformers);
  }

  @Override
  public FieldSort apply(final SearchFieldSort value) {
    final var field = value.field();
    final var order = toSortOrder(value);
    final var missing = value.missing();

    final var builder = SortOptionsBuilders.field().field(field).order(order);
    return missing == null ? builder.build() : builder.missing(FieldValue.of(missing)).build();
  }

  private SortOrder toSortOrder(final SearchFieldSort value) {
    if (value != null) {
      if (value.asc()) {
        return SortOrder.Asc;
      } else if (value.desc()) {
        return SortOrder.Desc;
      }
    }
    return null;
  }
}
