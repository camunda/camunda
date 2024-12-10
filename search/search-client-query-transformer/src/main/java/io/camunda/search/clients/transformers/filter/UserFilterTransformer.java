/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.or;
import static io.camunda.search.clients.query.SearchQueryBuilders.term;
import static io.camunda.webapps.schema.descriptors.usermanagement.index.UserIndex.EMAIL;
import static io.camunda.webapps.schema.descriptors.usermanagement.index.UserIndex.KEY;
import static io.camunda.webapps.schema.descriptors.usermanagement.index.UserIndex.NAME;
import static io.camunda.webapps.schema.descriptors.usermanagement.index.UserIndex.USERNAME;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.filter.UserFilter;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class UserFilterTransformer implements FilterTransformer<UserFilter> {

  @Override
  public SearchQuery toSearchQuery(final UserFilter filter) {

    return and(
        filter.keys() == null || filter.keys().isEmpty() ? null : orTerms(KEY, filter.keys()),
        filter.username() == null ? null : term(USERNAME, filter.username()),
        filter.email() == null ? null : term(EMAIL, filter.email()),
        filter.name() == null ? null : term(NAME, filter.name()));
  }

  @Override
  public List<String> toIndices(final UserFilter filter) {
    return List.of("camunda-user-8.7.0_alias");
  }

  private SearchQuery orTerms(final String field, final Set<Long> values) {
    return or(values.stream().map(value -> term(field, value)).collect(Collectors.toList()));
  }
}
