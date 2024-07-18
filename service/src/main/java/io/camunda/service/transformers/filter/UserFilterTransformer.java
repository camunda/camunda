/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.term;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.service.search.filter.UserFilter;
import java.util.List;

public class UserFilterTransformer implements FilterTransformer<UserFilter> {

  @Override
  public SearchQuery toSearchQuery(final UserFilter filter) {
    if (filter.username() == null) {
      return null;
    }
    return term("username", filter.username());
  }

  @Override
  public List<String> toIndices(final UserFilter filter) {
    return List.of("zeebe-record-user");
  }
}
