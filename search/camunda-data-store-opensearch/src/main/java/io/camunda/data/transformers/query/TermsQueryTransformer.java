/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.transformers.query;

import io.camunda.data.clients.query.DataStoreTermsQuery;
import io.camunda.data.clients.types.DataStoreFieldValue;
import io.camunda.data.transformers.OpensearchTransformers;
import java.util.List;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;
import org.opensearch.client.opensearch._types.query_dsl.TermsQuery;
import org.opensearch.client.opensearch._types.query_dsl.TermsQueryField;

public final class TermsQueryTransformer
    extends QueryVariantTransformer<DataStoreTermsQuery, TermsQuery> {

  public TermsQueryTransformer(final OpensearchTransformers mappers) {
    super(mappers);
  }

  @Override
  public TermsQuery apply(final DataStoreTermsQuery value) {
    return QueryBuilders.terms().field(value.field()).terms(of(value.values())).build();
  }

  private <T> TermsQueryField of(final List<DataStoreFieldValue> values) {
    final var fieldValues = values.stream().map(fieldValueTransformer::apply).toList();
    return TermsQueryField.of(f -> f.value(fieldValues));
  }
}
