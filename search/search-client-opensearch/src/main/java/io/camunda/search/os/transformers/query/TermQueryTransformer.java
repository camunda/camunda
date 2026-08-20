/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.os.transformers.query;

import io.camunda.search.clients.query.SearchTermQuery;
import io.camunda.search.os.transformers.OpensearchTransformers;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;
import org.opensearch.client.opensearch._types.query_dsl.TermQuery;

public final class TermQueryTransformer extends QueryOptionTransformer<SearchTermQuery, TermQuery> {

  public TermQueryTransformer(final OpensearchTransformers transformers) {
    super(transformers);
  }

  @Override
  public TermQuery apply(final SearchTermQuery value) {
    final var field = value.field();
    final var transformer = getFieldValueTransformer();
    final var fieldValue = value.value();
    final var tranformedFieldValue = transformer.apply(fieldValue);
    return QueryBuilders.term()
        .field(field)
        .value(tranformedFieldValue)
        .caseInsensitive(value.caseInsensitive())
        .build();
  }
}
