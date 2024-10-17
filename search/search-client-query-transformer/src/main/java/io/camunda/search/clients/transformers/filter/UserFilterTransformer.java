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

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.filter.UserFilter;
import java.util.List;

public class UserFilterTransformer implements FilterTransformer<UserFilter> {

  @Override
  public SearchQuery toSearchQuery(final UserFilter filter) {

    return and(
        filter.key() == null ? null : term("key", filter.key()),
        filter.username() == null ? null : term("username", filter.username()),
        filter.email() == null ? null : term("email", filter.email()),
        filter.name() == null ? null : term("name", filter.name()));
  }

  @Override
  public List<String> toIndices(final UserFilter filter) {
    return List.of("users");
  }
}
