/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.transformers.query;

import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.elasticsearch._types.query_dsl.TermsQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermsQueryField;
import io.camunda.data.clients.query.DataStoreTermsQuery;
import io.camunda.data.clients.types.DataStoreFieldValue;
import io.camunda.data.transformers.ElasticsearchTransformers;
import java.util.List;

public final class TermsQueryTransformer
    extends QueryVariantTransformer<DataStoreTermsQuery, TermsQuery> {

  public TermsQueryTransformer(final ElasticsearchTransformers transformers) {
    super(transformers);
  }

  @Override
  public TermsQuery apply(final DataStoreTermsQuery value) {
    final var field = value.field();
    final var values = value.values();
    final var termsQueryField = of(values);

    return QueryBuilders.terms().field(field).terms(termsQueryField).build();
  }

  private <T> TermsQueryField of(final List<DataStoreFieldValue> values) {
    final var transformer = getFieldValueTransformer();
    final var fieldValues = values.stream().map(transformer::apply).toList();
    return TermsQueryField.of(f -> f.value(fieldValues));
  }
}
