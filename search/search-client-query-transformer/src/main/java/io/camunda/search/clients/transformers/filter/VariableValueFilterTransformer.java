/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.term;
import static io.camunda.search.clients.query.SearchQueryBuilders.variableOperations;
import static io.camunda.webapps.schema.descriptors.operate.template.VariableTemplate.NAME;
import static io.camunda.webapps.schema.descriptors.operate.template.VariableTemplate.VALUE;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.query.SearchQueryBuilders;
import io.camunda.search.clients.types.TypedValue;
import io.camunda.search.filter.VariableValueFilter;

public final class VariableValueFilterTransformer
    implements FilterTransformer<VariableValueFilter> {

  @Override
  public SearchQuery toSearchQuery(final VariableValueFilter value) {
    return toSearchQuery(value, "name", "value");
  }

  public SearchQuery toSearchQuery(
      final VariableValueFilter value, final String varName, final String varValue) {
    final var variableNameQuery = term(NAME, value.name());
    if (value.valueOperations().isEmpty()) {
      return variableNameQuery;
    }

    final var queries = variableOperations(VALUE, value.valueOperations());
    queries.add(variableNameQuery);
    return and(queries);
  }

  private SearchQuery of(final Object value, final String field) {
    final var typedValue = TypedValue.toTypedValue(value);
    return SearchQueryBuilders.term().field(field).value(typedValue).build().toSearchQuery();
  }
}
