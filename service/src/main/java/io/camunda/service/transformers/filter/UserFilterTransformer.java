/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.term;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.service.search.filter.UserFilter;
import java.util.List;

public class UserFilterTransformer implements FilterTransformer<UserFilter> {

  @Override
  public SearchQuery toSearchQuery(final UserFilter filter) {

    return and(
        filter.username() == null ? null : term("value.username", filter.username()),
        filter.email() == null ? null : term("value.email", filter.email()),
        filter.name() == null ? null : term("value.name", filter.name()));
  }

  @Override
  public List<String> toIndices(final UserFilter filter) {
    return List.of("zeebe-record-user");
  }
}
