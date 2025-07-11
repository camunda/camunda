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
import static io.camunda.webapps.schema.descriptors.template.VariableTemplate.NAME;
import static io.camunda.webapps.schema.descriptors.template.VariableTemplate.VALUE;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.filter.VariableValueFilter;
import java.util.ArrayList;

public final class VariableValueFilterTransformer
    implements FilterTransformer<VariableValueFilter> {

  @Override
  public SearchQuery toSearchQuery(final VariableValueFilter value) {
    return toSearchQuery(value, NAME, VALUE);
  }

  public SearchQuery toSearchQuery(
      final VariableValueFilter value, final String varName, final String varValue) {
    final var variableNameQuery = term(varName, value.name());
    if (value.valueOperations().isEmpty()) {
      return variableNameQuery;
    }

    final var queries = new ArrayList<SearchQuery>();
    queries.add(variableNameQuery);
    queries.addAll(variableOperations(varValue, value.valueOperations()));
    return and(queries);
  }
}
