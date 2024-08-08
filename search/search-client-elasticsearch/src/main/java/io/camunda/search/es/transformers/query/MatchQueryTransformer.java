/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.transformers.query;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import io.camunda.search.clients.query.SearchMatchQuery;
import io.camunda.search.es.transformers.ElasticsearchTransformers;

public final class MatchQueryTransformer
    extends QueryOptionTransformer<SearchMatchQuery, MatchQuery> {

  public MatchQueryTransformer(final ElasticsearchTransformers transformers) {
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
