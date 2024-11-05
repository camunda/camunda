/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.os.transformers.query;

import io.camunda.search.clients.query.SearchRangeQuery;
import io.camunda.search.os.transformers.OpensearchTransformers;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;
import org.opensearch.client.opensearch._types.query_dsl.RangeQuery;

public final class RangeQueryTransformer
    extends QueryOptionTransformer<SearchRangeQuery, RangeQuery> {

  public RangeQueryTransformer(final OpensearchTransformers transformers) {
    super(transformers);
  }

  @Override
  public RangeQuery apply(final SearchRangeQuery value) {
    final var field = value.field();
    final var builder = QueryBuilders.range().field(field);

    if (value.gt() != null) {
      builder.gt(of(value.gt()));
    }

    if (value.gte() != null) {
      builder.gte(of(value.gte()));
    }

    if (value.lt() != null) {
      builder.lt(of(value.lt()));
    }

    if (value.lte() != null) {
      builder.lte(of(value.lte()));
    }

    if (value.format() != null) {
      builder.format(value.format());
    }

    return builder.build();
  }

  private <T> JsonData of(final T value) {
    return JsonData.of(value);
  }
}
