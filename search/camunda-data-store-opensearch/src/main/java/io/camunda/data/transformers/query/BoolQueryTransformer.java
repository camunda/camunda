/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.transformers.query;

import io.camunda.data.clients.query.DataStoreBoolQuery;
import io.camunda.data.transformers.OpensearchTransformers;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;

public class BoolQueryTransformer extends QueryVariantTransformer<DataStoreBoolQuery, BoolQuery> {

  public BoolQueryTransformer(final OpensearchTransformers mappers) {
    super(mappers);
  }

  @Override
  public BoolQuery apply(final DataStoreBoolQuery value) {
    final var builder = QueryBuilders.bool();

    final var filter = value.filter();
    if (filter != null && !filter.isEmpty()) {
      final var transformedFilter = filter.stream().map(queryTransformer::apply).toList();
      builder.filter(transformedFilter);
    }

    final var must = value.must();
    if (must != null && !must.isEmpty()) {
      final var transformedMust = must.stream().map(queryTransformer::apply).toList();
      builder.must(transformedMust);
    }

    final var should = value.should();
    if (should != null && !should.isEmpty()) {
      final var transformedShould = should.stream().map(queryTransformer::apply).toList();
      builder.should(transformedShould);
    }

    final var mustNot = value.mustNot();
    if (mustNot != null && !mustNot.isEmpty()) {
      final var transformedMustNot = mustNot.stream().map(queryTransformer::apply).toList();
      builder.mustNot(transformedMustNot);
    }

    return builder.build();
  }
}
