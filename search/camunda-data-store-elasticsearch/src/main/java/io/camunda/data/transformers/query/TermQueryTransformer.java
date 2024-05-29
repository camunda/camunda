/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.transformers.query;

import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import io.camunda.data.clients.query.DataStoreTermQuery;
import io.camunda.data.transformers.ElasticsearchTransformers;

public class TermQueryTransformer extends QueryVariantTransformer<DataStoreTermQuery, TermQuery> {

  public TermQueryTransformer(final ElasticsearchTransformers transformers) {
    super(transformers);
  }

  @Override
  public TermQuery apply(final DataStoreTermQuery value) {
    final var field = value.field();
    final var transformer = getFieldValueTransformer();
    final var fieldValue = value.value();
    final var tranformedFieldValue = transformer.apply(fieldValue);

    return QueryBuilders.term().field(field).value(tranformedFieldValue).build();
  }
}
