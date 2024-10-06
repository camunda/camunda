/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.not;
import static io.camunda.search.clients.query.SearchQueryBuilders.range;
import static io.camunda.search.clients.query.SearchQueryBuilders.term;

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
    final var name = value.name();
    final var eq = value.eq();
    final var neq = value.neq();
    final var gt = value.gt();
    final var gte = value.gte();
    final var lt = value.lt();
    final var lte = value.lte();

    final var variableNameQuery = term(varName, name);
    final SearchQuery variableValueQuery;

    if (eq != null) {
      variableValueQuery = of(eq, varValue);
    } else if (neq != null) {
      variableValueQuery = not(of(neq, varValue));
    } else {
      final var builder = range().field(varValue);

      if (gt != null) {
        builder.gt(gt);
      }

      if (gte != null) {
        builder.gte(gte);
      }

      if (lt != null) {
        builder.lt(lt);
      }

      if (lte != null) {
        builder.lte(lte);
      }

      variableValueQuery = builder.build().toSearchQuery();
    }

    return and(variableNameQuery, variableValueQuery);
  }

  private SearchQuery of(final Object value, final String field) {
    final var typedValue = TypedValue.toTypedValue(value);
    return SearchQueryBuilders.term().field(field).value(typedValue).build().toSearchQuery();
  }
}
