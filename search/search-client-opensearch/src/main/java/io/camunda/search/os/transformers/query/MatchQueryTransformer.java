/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.os.transformers.query;

import io.camunda.search.clients.query.SearchMatchQuery;
import io.camunda.search.os.transformers.OpensearchTransformers;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.query_dsl.MatchQuery;
import org.opensearch.client.opensearch._types.query_dsl.Operator;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;

public final class MatchQueryTransformer
    extends QueryOptionTransformer<SearchMatchQuery, MatchQuery> {

  public MatchQueryTransformer(final OpensearchTransformers transformers) {
    super(transformers);
  }

  @Override
  public MatchQuery apply(final SearchMatchQuery value) {
    final var field = value.field();
    final var query = value.query();
    final var queryAsFieldValue = FieldValue.of(query);
    final var operator = value.operator();
    final var builder = QueryBuilders.match().field(field).query(queryAsFieldValue);

    if (operator != null) {
      switch (operator) {
        case AND:
          {
            builder.operator(Operator.And);
            break;
          }
        case OR:
          {
            builder.operator(Operator.Or);
            break;
          }
        default:
          {
            break;
          }
      }
    }

    return builder.build();
  }
}
