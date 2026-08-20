/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.os.transformers.query;

import io.camunda.search.clients.query.SearchTermsQuery;
import io.camunda.search.clients.types.TypedValue;
import io.camunda.search.os.transformers.OpensearchTransformers;
import java.util.List;
import java.util.stream.Collectors;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;
import org.opensearch.client.opensearch._types.query_dsl.TermsQuery;
import org.opensearch.client.opensearch._types.query_dsl.TermsQueryField;

public final class TermsQueryTransformer
    extends QueryOptionTransformer<SearchTermsQuery, TermsQuery> {

  public TermsQueryTransformer(final OpensearchTransformers transformers) {
    super(transformers);
  }

  @Override
  public TermsQuery apply(final SearchTermsQuery value) {
    final var field = value.field();
    final var values = value.values();
    final var termsQueryField = of(values);
    return QueryBuilders.terms().field(field).terms(termsQueryField).build();
  }

  private <T> TermsQueryField of(final List<TypedValue> values) {
    final var transformer = getFieldValueTransformer();
    final var fieldValues = values.stream().map(transformer::apply).collect(Collectors.toList());
    return TermsQueryField.of(f -> f.value(fieldValues));
  }
}
