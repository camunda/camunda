/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.transformers.query;

import io.camunda.data.clients.query.DataStoreBoolQuery;
import io.camunda.data.clients.query.DataStoreQuery;
import io.camunda.data.transformers.OpensearchTransformers;
import java.util.List;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;

public class BoolQueryTransformer extends QueryVariantTransformer<DataStoreBoolQuery, BoolQuery> {

  public BoolQueryTransformer(final OpensearchTransformers mappers) {
    super(mappers);
  }

  @Override
  public BoolQuery apply(final DataStoreBoolQuery value) {
    final var builder = QueryBuilders.bool();
    final var filter = value.filter();
    final var must = value.must();
    final var should = value.should();
    final var mustNot = value.mustNot();

    if (filter != null && !filter.isEmpty()) {
      builder.filter(of(filter));
    }

    if (must != null && !must.isEmpty()) {
      builder.must(of(must));
    }

    if (should != null && !should.isEmpty()) {
      builder.should(of(should));
    }

    if (mustNot != null && !mustNot.isEmpty()) {
      builder.mustNot(of(mustNot));
    }

    return builder.build();
  }

  private List<Query> of(final List<DataStoreQuery> values) {
    final var transformer = getQueryTransformer();
    return values.stream().map(transformer::apply).toList();
  }
}
